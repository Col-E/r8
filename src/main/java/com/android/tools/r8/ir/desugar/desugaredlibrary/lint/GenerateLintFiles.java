// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Version;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Backend;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProgramClass.ChecksumSupplier;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.MethodAnnotation;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Timing;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class GenerateLintFiles extends AbstractGenerateFiles {

  // Only recent versions of studio support the format with fields.
  private static final boolean FORMAT_WITH_FIELD = true;

  public static GenerateLintFiles createForTesting(
      Path specification, Set<Path> implementation, Path outputDirectory) throws Exception {
    return new GenerateLintFiles(specification, implementation, outputDirectory);
  }

  public GenerateLintFiles(
      String desugarConfigurationPath, String desugarImplementationPath, String outputDirectory)
      throws Exception {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory);
  }

  public GenerateLintFiles(
      Path desugarConfigurationPath,
      Collection<Path> desugarImplementationPath,
      Path outputDirectory)
      throws Exception {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory);
  }

  private CfCode buildEmptyThrowingCfCode(DexMethod method) {
    CfInstruction insn[] = {new CfConstNull(), new CfThrow()};
    return new CfCode(method.holder, 1, method.proto.parameters.size() + 1, Arrays.asList(insn));
  }

  private void addMethodsToHeaderJar(
      DexApplication.Builder builder, DexClass clazz, Collection<DexEncodedMethod> methods) {
    if (methods.size() == 0) {
      return;
    }

    List<DexEncodedMethod> directMethods = new ArrayList<>();
    List<DexEncodedMethod> virtualMethods = new ArrayList<>();
    for (DexEncodedMethod method : methods) {
      assert method.getHolderType() == clazz.type;
      CfCode code = null;
      if (!method.accessFlags.isAbstract() /*&& !method.accessFlags.isNative()*/) {
        code = buildEmptyThrowingCfCode(method.getReference());
      }
      DexEncodedMethod throwingMethod =
          DexEncodedMethod.builder()
              .setMethod(method.getReference())
              .setAccessFlags(method.accessFlags)
              .setGenericSignature(MethodTypeSignature.noSignature())
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .disableAndroidApiLevelCheck()
              .build();
      if (method.isStatic() || method.isDirectMethod()) {
        directMethods.add(throwingMethod);
      } else {
        virtualMethods.add(throwingMethod);
      }
    }

    DexEncodedMethod[] directMethodsArray = new DexEncodedMethod[directMethods.size()];
    DexEncodedMethod[] virtualMethodsArray = new DexEncodedMethod[virtualMethods.size()];
    directMethods.toArray(directMethodsArray);
    virtualMethods.toArray(virtualMethodsArray);
    assert !options.encodeChecksums;
    ChecksumSupplier checksumSupplier = DexProgramClass::invalidChecksumRequest;
    DexProgramClass programClass =
        new DexProgramClass(
            clazz.type,
            null,
            Origin.unknown(),
            clazz.accessFlags,
            clazz.superType,
            clazz.interfaces,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            MethodCollectionFactory.fromMethods(directMethodsArray, virtualMethodsArray),
            false,
            checksumSupplier);
    builder.addProgramClass(programClass);
  }

  private String lintBaseFileName(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel) {
    return "desugared_apis_" + compilationApiLevel.getLevel() + "_" + minApiLevel.getLevel();
  }

  private Path lintFile(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel, String extension)
      throws Exception {
    Path directory = outputDirectory.resolve("compile_api_level_" + compilationApiLevel.getLevel());
    Files.createDirectories(directory);
    return Paths.get(
        directory
            + File.separator
            + lintBaseFileName(compilationApiLevel, minApiLevel)
            + extension);
  }

  private void writeLintFiles(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      SupportedClasses supportedClasses)
      throws Exception {
    // Build a plain text file with the desugared APIs.
    List<String> desugaredApisSignatures = new ArrayList<>();

    LazyLoadedDexApplication.Builder builder = DexApplication.builder(options, Timing.empty());
    supportedClasses.forEachClass(
        (supportedClass) -> {
          String classBinaryName =
              DescriptorUtils.getClassBinaryNameFromDescriptor(
                  supportedClass.getType().descriptor.toString());
          if (!supportedClass.getClassAnnotation().isFullySupported()) {
            supportedClass.forEachMethodAndAnnotation(
                (method, methodAnnotation) -> {
                  if (method.isInstanceInitializer() || method.isClassInitializer()) {
                    // No new constructors are added.
                    return;
                  }
                  if (shouldAddMethodToLint(methodAnnotation, minApiLevel)) {
                    desugaredApisSignatures.add(
                        classBinaryName
                            + '#'
                            + method.getReference().name
                            + method.getReference().proto.toDescriptorString());
                  }
                });
            if (FORMAT_WITH_FIELD) {
              supportedClass.forEachFieldAndAnnotation(
                  (field, fieldAnnotation) -> {
                    if (fieldAnnotation == null || !fieldAnnotation.unsupportedInMinApiRange) {
                      desugaredApisSignatures.add(
                          classBinaryName + '#' + field.getReference().name);
                    }
                  });
            }
          } else {
            desugaredApisSignatures.add(classBinaryName);
          }

          addMethodsToHeaderJar(
              builder, supportedClass.getClazz(), supportedClass.getSupportedMethods());
        });

    // Write a plain text file with the desugared APIs.
    desugaredApisSignatures.sort(Comparator.naturalOrder());
    FileUtils.writeTextFile(
        lintFile(compilationApiLevel, minApiLevel, ".txt"), desugaredApisSignatures);

    // Write a header jar with the desugared APIs.
    AppView<?> appView =
        AppView.createForD8(
            AppInfo.createInitialAppInfo(
                builder.build(), GlobalSyntheticsStrategy.forNonSynthesizing()));
    Marker marker =
        new Marker(Tool.D8)
            .setVersion(Version.LABEL)
            .setCompilationMode(CompilationMode.DEBUG)
            .setBackend(Backend.CF);
    CfApplicationWriter writer = new CfApplicationWriter(appView, marker);
    ClassFileConsumer consumer =
        new ClassFileConsumer.ArchiveConsumer(
            lintFile(compilationApiLevel, minApiLevel, FileUtils.JAR_EXTENSION));
    writer.write(consumer);
    consumer.finished(options.reporter);
  }

  private boolean shouldAddMethodToLint(
      MethodAnnotation methodAnnotation, AndroidApiLevel minApiLevel) {
    if (methodAnnotation == null) {
      return true;
    }
    if (methodAnnotation.unsupportedInMinApiRange) {
      // Do not lint method which are unavailable with some min apis.
      return false;
    }
    if (methodAnnotation.parallelStreamMethod) {
      return minApiLevel == AndroidApiLevel.L;
    }
    assert methodAnnotation.missingFromLatestAndroidJar;
    // We add missing methods from the latest jar, javac will add the squikles.
    return true;
  }

  private void generateLintFiles(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      SupportedClasses supportedMethods)
      throws Exception {
    System.out.print("  - generating for min API:");
    System.out.print(" " + minApiLevel);
    writeLintFiles(compilationApiLevel, minApiLevel, supportedMethods);
  }

  @Override
  public AndroidApiLevel run() throws Exception {
    AndroidApiLevel compilationLevel =
        desugaredLibrarySpecification.getRequiredCompilationApiLevel();
    SupportedClasses supportedMethods =
        new SupportedClassesGenerator(options)
            .run(desugaredLibraryImplementation, desugaredLibrarySpecificationPath);
    System.out.println("Generating lint files for compile API " + compilationLevel);
    generateLintFiles(compilationLevel, AndroidApiLevel.B, supportedMethods);
    generateLintFiles(compilationLevel, AndroidApiLevel.L, supportedMethods);
    return compilationLevel;
  }

  public static void main(String[] args) throws Exception {
    AbstractGenerateFiles.main(args);
  }
}
