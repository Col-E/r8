// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

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

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> defaultTreeShakingParameters() {
    return data(MinifyMode.values());
  }

  public static List<Object[]> data(MinifyMode[] minifyModes) {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build(), minifyModes);
  }

  protected abstract String getName();

  protected abstract String getMainClass();

  private final TestParameters parameters;
  private final MinifyMode minify;

  public TestParameters getParameters() {
    return parameters;
  }

  public MinifyMode getMinify() {
    return minify;
  }

  public TreeShakingTest(TestParameters parameters, MinifyMode minify) {
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

    String programFile = ToolHelper.TESTS_BUILD_DIR + getName() + ".jar";

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
            .addKeepRuleFiles(
                ListUtils.map(
                    keepRulesFiles,
                    keepRulesFile -> Paths.get(ToolHelper.getProjectRoot(), keepRulesFile)))
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
    Path shakingLib =
        testForD8(Backend.DEX)
            .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "shakinglib.jar"))
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    Consumer<ArtCommandBuilder> extraArtArgs =
        builder -> {
          builder.appendClasspath(shakingLib.toString());
        };
    String d8Output =
        testForD8(Backend.DEX)
            .setMinApi(parameters)
            .addProgramFiles(Paths.get(programFile))
            .compile()
            .writeToZip()
            .toString();
    DexVm dexVm = parameters.getRuntime().asDex().getVm();
    if (outputComparator != null) {
      String output1 =
          ToolHelper.runArtNoVerificationErrors(
              Collections.singletonList(d8Output), getMainClass(), extraArtArgs, dexVm);
      String output2 =
          ToolHelper.runArtNoVerificationErrors(
              Collections.singletonList(outJar.toString()), getMainClass(), extraArtArgs, dexVm);
      outputComparator.accept(output1, output2);
    } else {
      ToolHelper.checkArtOutputIdentical(
          Collections.singletonList(d8Output),
          Collections.singletonList(outJar.toString()),
          getMainClass(),
          extraArtArgs,
          null);
    }
    if (dexComparator != null) {
      CodeInspector ref = new CodeInspector(Paths.get(d8Output));
      dexComparator.accept(ref, compileResult.inspector());
    }
    if (inspection != null) {
      compileResult.inspect(inspection);
    }
  }
}
