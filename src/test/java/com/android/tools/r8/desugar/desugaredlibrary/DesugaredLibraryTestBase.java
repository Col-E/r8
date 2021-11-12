// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestState;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfigurationParser;
import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class DesugaredLibraryTestBase extends TestBase {

  private static final boolean FORCE_JDK11_DESUGARED_LIB = false;

  @BeforeClass
  public static void setUpDesugaredLibrary() {
    if (!FORCE_JDK11_DESUGARED_LIB) {
      return;
    }
    System.setProperty("desugar_jdk_json_dir", "src/library_desugar/jdk11");
    System.setProperty(
        "desugar_jdk_libs", "third_party/openjdk/desugar_jdk_libs_11/desugar_jdk_libs.jar");
    System.out.println("Forcing the usage of JDK11 desugared library.");
  }

  public static boolean isJDK11DesugaredLibrary() {
    String property = System.getProperty("desugar_jdk_json_dir", "");
    return property.contains("jdk11");
  }

  // For conversions tests, we need DexRuntimes where classes to convert are present (DexRuntimes
  // above N and O depending if Stream or Time APIs are used), but we need to compile the program
  // with a minAPI below to force the use of conversions.
  protected static TestParametersCollection getConversionParametersUpToExcluding(
      AndroidApiLevel apiLevel) {
    if (apiLevel == AndroidApiLevel.N) {
      return getTestParameters()
          .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
          .withApiLevelsEndingAtExcluding(AndroidApiLevel.N)
          .build();
    }
    if (apiLevel == AndroidApiLevel.O) {
      return getTestParameters()
          .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
          .withApiLevelsEndingAtExcluding(AndroidApiLevel.O)
          .build();
    }
    throw new Error("Unsupported conversion parameters");
  }

  protected boolean requiresEmulatedInterfaceCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  protected boolean requiresAnyCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.O.getLevel();
  }

  protected L8TestBuilder testForL8(AndroidApiLevel apiLevel) {
    return L8TestBuilder.create(apiLevel, Backend.DEX, new TestState(temp));
  }

  protected L8TestBuilder testForL8(AndroidApiLevel apiLevel, Backend backend) {
    return L8TestBuilder.create(apiLevel, backend, new TestState(temp));
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) {
    return buildDesugaredLibrary(apiLevel, null, false);
  }

  protected Path buildDesugaredLibrary(
      AndroidApiLevel apiLevel, Consumer<InternalOptions> optionsModifier) {
    return buildDesugaredLibrary(apiLevel, null, false, ImmutableList.of(), optionsModifier);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules) {
    return buildDesugaredLibrary(apiLevel, keepRules, true);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules, boolean shrink) {
    return buildDesugaredLibrary(apiLevel, keepRules, shrink, ImmutableList.of(), options -> {});
  }

  protected Path buildDesugaredLibrary(
      AndroidApiLevel apiLevel,
      String keepRules,
      boolean shrink,
      List<Path> additionalProgramFiles) {
    return buildDesugaredLibrary(
        apiLevel, keepRules, shrink, additionalProgramFiles, options -> {});
  }

  protected Path buildDesugaredLibrary(
      AndroidApiLevel apiLevel,
      String generatedKeepRules,
      boolean release,
      List<Path> additionalProgramFiles,
      Consumer<InternalOptions> optionsModifier) {
    try {
      return testForL8(apiLevel)
          .addProgramFiles(additionalProgramFiles)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .applyIf(
              release,
              builder -> {
                if (generatedKeepRules != null && !generatedKeepRules.trim().isEmpty()) {
                  builder.addGeneratedKeepRules(generatedKeepRules);
                }
              },
              L8TestBuilder::setDebug)
          .addOptionsModifier(optionsModifier)
          .setDesugarJDKLibsConfiguration(ToolHelper.DESUGAR_LIB_CONVERSIONS)
          // If we compile extended library here, it means we use TestNG. TestNG requires
          // annotations, hence we disable annotation removal. This implies that extra warnings are
          // generated.
          .setDisableL8AnnotationRemoval(!additionalProgramFiles.isEmpty())
          .compile()
          .applyIf(
              additionalProgramFiles.isEmpty(),
              builder ->
                  builder.inspectDiagnosticMessages(
                      diagnostics ->
                          assertTrue(
                              // TODO(b/193597730): Fix JDK11 desugared library message,
                              isJDK11DesugaredLibrary()
                                  || diagnostics.getInfos().stream()
                                      .noneMatch(
                                          string ->
                                              string
                                                  .getDiagnosticMessage()
                                                  .startsWith(
                                                      "Invalid parameter counts in MethodParameter"
                                                          + " attributes.")))))
          .writeToZip();
    } catch (CompilationFailedException | ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(
          "Different lines: " + lines[i] + " || " + lines[i + 1] + "\n" + stdOut,
          lines[i],
          lines[i + 1]);
    }
  }

  protected static Path[] getAllFilesWithSuffixInDirectory(Path directory, String suffix)
      throws IOException {
    return Files.walk(directory)
        .filter(path -> path.toString().endsWith(suffix))
        .toArray(Path[]::new);
  }

  protected KeepRuleConsumer createKeepRuleConsumer(TestParameters parameters) {
    return LibraryDesugaringTestConfiguration.createKeepRuleConsumer(parameters);
  }

  public Path getDesugaredLibraryInCF(
      TestParameters parameters, Consumer<InternalOptions> configurationForLibraryCompilation)
      throws IOException, CompilationFailedException {
    Path desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs.jar");
    L8Command.Builder l8Builder =
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .setMode(CompilationMode.DEBUG)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(desugaredLib, OutputMode.ClassFile);

    ToolHelper.runL8(l8Builder.build(), configurationForLibraryCompilation);
    return desugaredLib;
  }

  protected DesugaredLibraryConfiguration configurationWithSupportAllCallbacksFromLibrary(
      InternalOptions options,
      boolean libraryCompilation,
      TestParameters parameters,
      boolean supportAllCallbacksFromLibrary) {
    return new DesugaredLibraryConfigurationParser(
            options.dexItemFactory(),
            options.reporter,
            libraryCompilation,
            parameters.getApiLevel().getLevel())
        .parse(
            StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()),
            builder -> builder.setSupportAllCallbacksFromLibrary(supportAllCallbacksFromLibrary));
  }

  private Map<AndroidApiLevel, Path> desugaredLibraryClassFileCache = new HashMap<>();

  // Build the desugared library in class file format.
  public Path buildDesugaredLibraryClassFile(AndroidApiLevel apiLevel) throws Exception {
    Path desugaredLib = desugaredLibraryClassFileCache.get(apiLevel);
    if (desugaredLib != null) {
      return desugaredLib;
    }
    desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs.jar");
    L8Command.Builder l8Builder =
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .setMode(CompilationMode.DEBUG)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(desugaredLib, OutputMode.ClassFile);
    ToolHelper.runL8(l8Builder.build());
    desugaredLibraryClassFileCache.put(apiLevel, desugaredLib);
    return desugaredLib;
  }

  public String collectKeepRulesWithTraceReferences(
      Path desugaredProgramClassFile, Path desugaredLibraryClassFile) throws Exception {
    Path generatedKeepRules = temp.newFile().toPath();
    TraceReferences.run(
        "--keep-rules",
        "--lib",
        ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
        "--target",
        desugaredLibraryClassFile.toString(),
        "--source",
        desugaredProgramClassFile.toString(),
        "--output",
        generatedKeepRules.toString(),
        "--map-diagnostics",
        "error",
        "info");
    return FileUtils.readTextFile(generatedKeepRules, Charsets.UTF_8);
  }

  protected static ClassFileInfo extractClassFileInfo(byte[] classFileBytes) {
    class ClassFileInfoExtractor extends ClassVisitor {
      private String classBinaryName;
      private List<String> interfaces = new ArrayList<>();
      private final List<String> methodNames = new ArrayList<>();

      private ClassFileInfoExtractor() {
        super(ASM_VERSION);
      }

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        classBinaryName = name;
        this.interfaces.addAll(Arrays.asList(interfaces));
        super.visit(version, access, name, signature, superName, interfaces);
      }

      @Override
      public MethodVisitor visitMethod(
          int access, String name, String desc, String signature, String[] exceptions) {
        methodNames.add(name);
        return super.visitMethod(access, name, desc, signature, exceptions);
      }

      ClassFileInfo getClassFileInfo() {
        return new ClassFileInfo(classBinaryName, interfaces, methodNames);
      }
    }

    ClassReader reader = new ClassReader(classFileBytes);
    ClassFileInfoExtractor extractor = new ClassFileInfoExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassFileInfo();
  }

  public interface KeepRuleConsumer extends StringConsumer {

    String get();

    static KeepRuleConsumer emptyConsumer() {
      return new KeepRuleConsumer() {

        @Override
        public String get() {
          throw new Unreachable();
        }

        @Override
        public void accept(String string, DiagnosticsHandler handler) {
          // Intentionally empty.
        }
      };
    }
  }

  protected static class ClassFileInfo {
    private final String classBinaryName;
    private List<String> interfaces;
    private final List<String> methodNames;

    ClassFileInfo(String classBinaryNamename, List<String> interfaces, List<String> methodNames) {
      this.classBinaryName = classBinaryNamename;
      this.interfaces = interfaces;
      this.methodNames = methodNames;
    }

    public String getClassBinaryName() {
      return classBinaryName;
    }

    public List<String> getInterfaces() {
      return interfaces;
    }

    public List<String> getMethodNames() {
      return methodNames;
    }
  }
}
