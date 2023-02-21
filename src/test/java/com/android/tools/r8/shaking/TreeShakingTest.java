// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base class of individual tree shaking tests in com.android.tools.r8.shaking.examples.
 * <p>
 * To add a new test, add Java files and keep rules to a new subdirectory of src/test/examples and
 * add a new subclass of TreeShakingTest. To run with multiple minification modes and
 * frontend/backend combinations, copy the Parameterized setup from one of the existing subclasses,
 * e.g. {@link com.android.tools.r8.shaking.examples.TreeShaking1Test}. Then create a test method
 * that calls {@link TreeShakingTest::runTest}, passing in the path to your keep rule file and
 * lambdas to determine if the right bits of the application are kept or discarded.
 */
public abstract class TreeShakingTest extends TestBase {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static List<Object[]> defaultTreeShakingParameters() {
    return data(Frontend.values(), MinifyMode.values());
  }

  public static List<Object[]> data(MinifyMode[] minifyModes) {
    return data(Frontend.values(), minifyModes);
  }

  public static List<Object[]> data(Frontend[] frontends, MinifyMode[] minifyModes) {
    return buildParameters(
        frontends, getTestParameters().withAllRuntimesAndApiLevels().build(), minifyModes);
  }

  protected abstract String getName();

  protected abstract String getMainClass();

  protected enum Frontend {
    DEX, JAR
  }

  private final Frontend frontend;
  private final TestParameters parameters;
  private final MinifyMode minify;

  public Frontend getFrontend() {
    return frontend;
  }

  public TestParameters getParameters() {
    return parameters;
  }

  public MinifyMode getMinify() {
    return minify;
  }

  public TreeShakingTest(Frontend frontend, TestParameters parameters, MinifyMode minify) {
    this.frontend = frontend;
    this.parameters = parameters;
    this.minify = minify;
  }

  protected static void checkSameStructure(CodeInspector ref, CodeInspector inspector) {
    ref.forAllClasses(
        refClazz ->
            checkSameStructure(
                refClazz, inspector.clazz(refClazz.getDexProgramClass().toSourceString())));
  }

  private static void checkSameStructure(ClassSubject refClazz, ClassSubject clazz) {
    Assert.assertTrue(clazz.isPresent());
    refClazz.forAllFields(refField -> checkSameStructure(refField, clazz));
    refClazz.forAllMethods(refMethod -> checkSameStructure(refMethod, clazz));
  }

  private static void checkSameStructure(FoundMethodSubject refMethod, ClassSubject clazz) {
    MethodSignature signature = refMethod.getOriginalSignature();
    // Don't check for existence of class initializers, as the code optimization can remove them.
    if (!refMethod.isClassInitializer()) {
      Assert.assertTrue(
          "Missing Method: "
              + clazz.getDexProgramClass().toSourceString()
              + "."
              + signature.toString(),
          clazz.method(signature).isPresent());
    }
  }

  private static void checkSameStructure(FoundFieldSubject refField, ClassSubject clazz) {
    FieldSignature signature = refField.getOriginalSignature();
    Assert.assertTrue(
        "Missing field: " + signature.type + " " + clazz.getOriginalDescriptor()
            + "." + signature.name,
        clazz.field(signature.type, signature.name).isPresent());
  }

  protected void runTest(
      ThrowingConsumer<CodeInspector, Exception> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<CodeInspector, CodeInspector> dexComparator,
      List<String> keepRulesFiles)
      throws Exception {
    runTest(inspection, outputComparator, dexComparator, keepRulesFiles, null, null, null);
  }

  protected void runTest(
      ThrowingConsumer<CodeInspector, Exception> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<CodeInspector, CodeInspector> dexComparator,
      List<String> keepRulesFiles,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    runTest(
        inspection, outputComparator, dexComparator, keepRulesFiles, optionsConsumer, null, null);
  }

  protected void runTest(
      ThrowingConsumer<CodeInspector, Exception> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<CodeInspector, CodeInspector> dexComparator,
      List<String> keepRulesFiles,
      Consumer<InternalOptions> optionsConsumer,
      ThrowableConsumer<R8FullTestBuilder> testBuilderConsumer)
      throws Exception {
    runTest(
        inspection,
        outputComparator,
        dexComparator,
        keepRulesFiles,
        optionsConsumer,
        testBuilderConsumer,
        null);
  }

  protected void runTest(
      ThrowingConsumer<CodeInspector, Exception> inspection,
      BiConsumer<String, String> outputComparator,
      BiConsumer<CodeInspector, CodeInspector> dexComparator,
      List<String> keepRulesFiles,
      Consumer<InternalOptions> optionsConsumer,
      ThrowableConsumer<R8FullTestBuilder> testBuilderConsumer,
      DiagnosticsConsumer diagnosticsConsumer)
      throws Exception {
    assumeFalse(frontend == Frontend.DEX && parameters.isCfRuntime());
    String originalDex = ToolHelper.TESTS_BUILD_DIR + getName() + "/classes.dex";
    String programFile =
        frontend == Frontend.DEX ? originalDex : ToolHelper.TESTS_BUILD_DIR + getName() + ".jar";
    R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            // Go through app builder to add dex files.
            .apply(
                b ->
                    ToolHelper.getAppBuilder(b.getBuilder())
                        .addProgramFiles(Paths.get(programFile)))
            .enableProguardTestOptions()
            .minification(minify.isMinify())
            .setMinApi(parameters)
            .addKeepRuleFiles(ListUtils.map(keepRulesFiles, Paths::get))
            .addLibraryFiles(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib.jar"))
            .addDefaultRuntimeLibrary(parameters)
            .addOptionsModification(
                options -> {
                  options.inlinerOptions().enableInlining = programFile.contains("inlining");
                  if (optionsConsumer != null) {
                    optionsConsumer.accept(options);
                  }
                })
            .allowStdoutMessages()
            .applyIf(testBuilderConsumer != null, testBuilderConsumer);
    R8TestCompileResult compileResult =
        diagnosticsConsumer == null
            ? testBuilder.compile()
            : testBuilder.compileWithExpectedDiagnostics(diagnosticsConsumer);
    Path outJar = compileResult.writeToZip();
    if (parameters.isCfRuntime()) {
      Path shakinglib = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "shakinglib.jar");
      CfRuntime cfRuntime = parameters.getRuntime().asCf();
      ProcessResult resultInput =
          ToolHelper.runJava(
              cfRuntime, Arrays.asList(Paths.get(programFile), shakinglib), getMainClass());
      Assert.assertEquals(0, resultInput.exitCode);
      ProcessResult resultOutput =
          ToolHelper.runJava(cfRuntime, Arrays.asList(outJar, shakinglib), getMainClass());
      if (outputComparator != null) {
        outputComparator.accept(resultInput.stdout, resultOutput.stdout);
      } else {
        Assert.assertEquals(resultInput.toString(), resultOutput.toString());
      }
      if (inspection != null) {
        compileResult.inspect(inspection);
      }
      return;
    }
    if (!ToolHelper.artSupported() && !ToolHelper.compareAgaintsGoldenFiles()) {
      return;
    }
    Consumer<ArtCommandBuilder> extraArtArgs = builder -> {
      builder.appendClasspath(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib/classes.dex");
    };
    DexVm dexVm = parameters.getRuntime().asDex().getVm();
    if (Files.exists(Paths.get(originalDex))) {
      if (outputComparator != null) {
        String output1 =
            ToolHelper.runArtNoVerificationErrors(
                Collections.singletonList(originalDex), getMainClass(), extraArtArgs, dexVm);
        String output2 =
            ToolHelper.runArtNoVerificationErrors(
                Collections.singletonList(outJar.toString()), getMainClass(), extraArtArgs, dexVm);
        outputComparator.accept(output1, output2);
      } else {
        ToolHelper.checkArtOutputIdentical(
            Collections.singletonList(originalDex),
            Collections.singletonList(outJar.toString()),
            getMainClass(),
            extraArtArgs,
            null);
      }
      if (dexComparator != null) {
        CodeInspector ref = new CodeInspector(Paths.get(originalDex));
        dexComparator.accept(ref, compileResult.inspector());
      }
    } else {
      Assert.assertNull(outputComparator);
      Assert.assertNull(dexComparator);
      ToolHelper.runArtNoVerificationErrors(
          Collections.singletonList(outJar.toString()), getMainClass(), extraArtArgs, dexVm);
    }
    if (inspection != null) {
      compileResult.inspect(inspection);
    }
  }
}
