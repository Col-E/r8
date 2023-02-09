// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public abstract class AbstractGenerateFiles {

  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";

  // If we increment this api level, we need to verify everything works correctly.
  static final AndroidApiLevel MAX_TESTED_ANDROID_API_LEVEL = AndroidApiLevel.T;

  private final DexItemFactory factory = new DexItemFactory();
  private final Reporter reporter = new Reporter();
  final InternalOptions options = new InternalOptions(factory, reporter);

  final MachineDesugaredLibrarySpecification desugaredLibrarySpecification;
  final Collection<Path> desugaredLibraryImplementation;
  final Path outputDirectory;
  final Set<DexMethod> parallelMethods = Sets.newIdentityHashSet();

  public AbstractGenerateFiles(
      String desugarConfigurationPath, String desugarImplementationPath, String outputDirectory)
      throws Exception {
    this(
        Paths.get(desugarConfigurationPath),
        ImmutableList.of(Paths.get(desugarImplementationPath)),
        Paths.get(outputDirectory));
  }

  AbstractGenerateFiles(
      Path desugarConfigurationPath,
      Collection<Path> desugarImplementationPath,
      Path outputDirectory)
      throws Exception {
    DesugaredLibrarySpecification specification =
        readDesugaredLibraryConfiguration(desugarConfigurationPath);
    Path androidJarPath = getAndroidJarPath(specification.getRequiredCompilationApiLevel());
    DexApplication app = createApp(androidJarPath, options);
    this.desugaredLibrarySpecification = specification.toMachineSpecification(app, Timing.empty());
    this.desugaredLibraryImplementation = desugarImplementationPath;
    this.outputDirectory = outputDirectory;
    if (!Files.isDirectory(this.outputDirectory)) {
      throw new Exception("Output directory " + outputDirectory + " is not a directory");
    }

    fillParallelMethods();
  }

  private DesugaredLibrarySpecification readDesugaredLibraryConfiguration(
      Path desugarConfigurationPath) {
    return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
        StringResource.fromFile(desugarConfigurationPath),
        factory,
        reporter,
        false,
        AndroidApiLevel.B.getLevel());
  }

  private static DexApplication createApp(Path androidLib, InternalOptions options)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    AndroidApp inputApp = builder.addLibraryFiles(androidLib).build();
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication app = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return app;
  }

  private void fillParallelMethods() {
    DexType streamType = factory.createType(factory.createString("Ljava/util/stream/Stream;"));
    DexMethod parallelMethod =
        factory.createMethod(
            factory.collectionType,
            factory.createProto(streamType),
            factory.createString("parallelStream"));
    parallelMethods.add(parallelMethod);
    DexType baseStreamType =
        factory.createType(factory.createString("Ljava/util/stream/BaseStream;"));
    for (String typePrefix : new String[] {"Base", "Double", "Int", "Long"}) {
      streamType =
          factory.createType(factory.createString("Ljava/util/stream/" + typePrefix + "Stream;"));
      parallelMethod =
          factory.createMethod(
              streamType, factory.createProto(streamType), factory.createString("parallel"));
      parallelMethods.add(parallelMethod);
      // Also filter out the generated bridges for the covariant return type.
      parallelMethod =
          factory.createMethod(
              streamType, factory.createProto(baseStreamType), factory.createString("parallel"));
      parallelMethods.add(parallelMethod);
    }
  }

  public static class SupportedMethods {

    public final Set<DexClass> classesWithAllMethodsSupported;
    public final Map<DexClass, List<DexEncodedMethod>> supportedMethods;

    public SupportedMethods(
        Set<DexClass> classesWithAllMethodsSupported,
        Map<DexClass, List<DexEncodedMethod>> supportedMethods) {
      this.classesWithAllMethodsSupported = classesWithAllMethodsSupported;
      this.supportedMethods = supportedMethods;
    }
  }

  SupportedMethods collectSupportedMethods(
      AndroidApiLevel compilationApiLevel, Predicate<DexEncodedMethod> supported) throws Exception {

    // Read the android.jar for the compilation API level. Read it as program instead of library
    // to get the local information for parameter names.
    AndroidApp library =
        AndroidApp.builder().addProgramFiles(getAndroidJarPath(compilationApiLevel)).build();
    DirectMappedDexApplication dexApplication =
        new ApplicationReader(library, options, Timing.empty()).read().toDirect();

    AndroidApp implementation =
        AndroidApp.builder().addProgramFiles(desugaredLibraryImplementation).build();
    DirectMappedDexApplication implementationApplication =
        new ApplicationReader(implementation, options, Timing.empty()).read().toDirect();

    options.setDesugaredLibrarySpecification(desugaredLibrarySpecification);
    List<DexMethod> backports =
        BackportedMethodRewriter.generateListOfBackportedMethods(
            implementation, options, ThreadUtils.getExecutorService(1));

    // Collect all the methods that the library desugar configuration adds support for.
    Set<DexClass> classesWithAllMethodsSupported = Sets.newIdentityHashSet();
    Map<DexClass, List<DexEncodedMethod>> supportedMethods = new LinkedHashMap<>();
    for (DexProgramClass clazz : dexApplication.classes()) {
      if (clazz.accessFlags.isPublic() && desugaredLibrarySpecification.isSupported(clazz.type)) {
        DexProgramClass implementationClass =
            implementationApplication.programDefinitionFor(clazz.getType());
        if (implementationClass == null) {
          throw new Exception("Implementation class not found for " + clazz.toSourceString());
        }
        boolean allMethodsAdded = true;
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.isPublic()) {
            continue;
          }
          ProgramMethod implementationMethod =
              implementationClass.lookupProgramMethod(method.getReference());
          // Don't include methods which are not implemented by the desugared library.
          if (supported.test(method)
              && (implementationMethod != null || backports.contains(method.getReference()))) {
            supportedMethods.computeIfAbsent(clazz, k -> new ArrayList<>()).add(method);
          } else {
            allMethodsAdded = false;
          }
        }
        if (allMethodsAdded) {
          classesWithAllMethodsSupported.add(clazz);
        }
      }

      // All emulated interfaces static and default methods are supported.
      if (desugaredLibrarySpecification.getEmulatedInterfaces().containsKey(clazz.type)) {
        assert clazz.isInterface();
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.isDefaultMethod() && !method.isStatic()) {
            continue;
          }
          if (supported.test(method)) {
            supportedMethods.computeIfAbsent(clazz, k -> new ArrayList<>()).add(method);
          }
        }
      }
    }

    // All retargeted methods are supported.
    desugaredLibrarySpecification.forEachRetargetMethod(
        method -> {
          DexClass clazz = dexApplication.contextIndependentDefinitionFor(method.getHolderType());
          assert clazz != null;
          DexEncodedMethod encodedMethod = clazz.lookupMethod(method);
          if (encodedMethod == null) {
            // Some methods are registered but present higher in the hierarchy, ignore them.
            return;
          }
          if (supported.test(encodedMethod)) {
            supportedMethods.computeIfAbsent(clazz, k -> new ArrayList<>()).add(encodedMethod);
          }
        });

    return new SupportedMethods(classesWithAllMethodsSupported, supportedMethods);
  }

  static Path getAndroidJarPath(AndroidApiLevel apiLevel) {
    String jar =
        apiLevel == AndroidApiLevel.MASTER
            ? "third_party/android_jar/lib-master/android.jar"
            : String.format(ANDROID_JAR_PATTERN, apiLevel.getLevel());
    return Paths.get(jar);
  }

  abstract void run() throws Exception;

  public static void main(String[] args) throws Exception {
    if (args.length == 3) {
      new GenerateLintFiles(args[0], args[1], args[2]).run();
      return;
    }
    if (args.length == 4 && args[0].equals("--generate-api-docs")) {
      new GenerateHtmlDoc(args[1], args[2], args[3]).run();
      return;
    }
    throw new RuntimeException(
        StringUtils.joinLines(
            "Invalid invocation.",
            "Usage: GenerateLineFiles [--generate-api-docs] "
                + "<desugar configuration> <desugar implementation> <output directory>"));
  }
}
