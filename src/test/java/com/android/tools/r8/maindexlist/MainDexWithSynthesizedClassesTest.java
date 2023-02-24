// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MainDexWithSynthesizedClassesTest extends TestBase {

  static final AndroidApiLevel nativeMultiDexLevel = AndroidApiLevel.L;

  static final String EXPECTED = StringUtils.lines("A");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsEndingAtExcluding(nativeMultiDexLevel)
        .build();
  }

  public MainDexWithSynthesizedClassesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testFinal() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      D8TestCompileResult compileResult =
          testForD8()
              .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
              .addMainDexKeepClassAndMemberRules(TestClass.class)
              .setMinApi(parameters)
              .compile();
      checkCompilationResult(compileResult);
    }
  }

  @Test
  public void testIntermediate() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult intermediateResult =
        testForD8()
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .setMinApi(parameters)
            .setIntermediate(true)
            .compile();
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(intermediateResult.writeToZip())
            .addMainDexKeepClassAndMemberRules(TestClass.class)
            .setMinApi(parameters)
            .compile();
    checkCompilationResult(compileResult);
  }

  @Test
  public void testSupportedMainDexListD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    // It remains a supported mode to first compile to DEX and then use tracing on the compiled
    // output. Neither the compilation, the trace or the merge should issue any diagnostics.
    Path dexed =
        testForD8()
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
            .writeToZip();

    Path mainDexFile = temp.newFile("maindex.list").toPath();
    testForMainDexListGenerator()
        .addLibraryFiles(ToolHelper.getFirstSupportedAndroidJar(parameters.getApiLevel()))
        .addProgramFiles(dexed)
        .addMainDexRules("-keep class " + typeName(TestClass.class) + "{ *; }")
        .setMainDexListOutputPath(mainDexFile)
        .run();

    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(dexed)
            .addMainDexListFiles(mainDexFile)
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
    checkCompilationResult(compileResult);
  }

  /**
   * This test checks for maintained support of including synthetics from main-dex-list entries in
   * the main-dex file. This test simulates that the tracing done at the class-file level has
   * determined that TestClass and A are both traced. Thus the synthetic lambda from A will be
   * included in the main-dex file.
   *
   * <p>TODO(b/181858113): Update to assert an error is raised once deprecated period is over.
   */
  @Test
  public void testDeprecatedSyntheticsFromMainDexListD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path mainDexFile = temp.newFile("maindex.list").toPath();
    FileUtils.writeTextFile(mainDexFile, binaryName(A.class) + ".class");
    D8TestCompileResult compileResult =
        testForD8()
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .addMainDexListClasses(TestClass.class)
            .addMainDexListFiles(mainDexFile)
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertOnlyWarnings()
                        .assertWarningsMatch(
                            // The "classes" addition has no origin.
                            allOf(
                                diagnosticType(UnsupportedMainDexListUsageDiagnostic.class),
                                diagnosticOrigin(Origin.unknown())),
                            // The "file" addition must have the file origin.
                            allOf(
                                diagnosticType(UnsupportedMainDexListUsageDiagnostic.class),
                                diagnosticOrigin(new PathOrigin(mainDexFile)))));
    checkCompilationResult(compileResult);
  }

  /**
   * This test checks for maintained support of including synthetics from main-dex-list entries in
   * the main-dex file. This test simulates that the tracing done at the class-file level has
   * determined that TestClass and A are both traced. Thus the synthetic lambda from A will be
   * included in the main-dex file.
   *
   * <p>TODO(b/181858113): Remove once deprecated main-dex-list is removed.
   */
  @Test
  public void testDeprecatedSyntheticsFromMainDexListR8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path mainDexFile = temp.newFile("maindex.list").toPath();
    FileUtils.writeTextFile(mainDexFile, binaryName(A.class) + ".class");
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .setMinApi(parameters)
            .addOptionsModification(o -> o.minimalMainDex = true)
            .addMainDexListClasses(TestClass.class)
            .addMainDexListFiles(mainDexFile)
            .addDontObfuscate()
            .noTreeShaking()
            .allowDiagnosticWarningMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertOnlyWarnings()
                        .assertWarningsMatch(
                            // The "classes" addition has no origin.
                            allOf(
                                diagnosticType(UnsupportedMainDexListUsageDiagnostic.class),
                                diagnosticOrigin(Origin.unknown())),
                            // The "file" addition must have the file origin.
                            allOf(
                                diagnosticType(UnsupportedMainDexListUsageDiagnostic.class),
                                diagnosticOrigin(new PathOrigin(mainDexFile)))));
    checkCompilationResult(compileResult, compileResult.app);
  }

  private void checkCompilationResult(D8TestCompileResult compileResult) throws Exception {
    checkCompilationResult(compileResult, compileResult.app);
  }

  private void checkCompilationResult(TestCompileResult<?, ?> compileResult, AndroidApp app)
      throws Exception {
    if (parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        < nativeMultiDexLevel.getLevel()) {
      compileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors();
    } else {
      compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput(EXPECTED);
    }
    Path out = temp.newFolder().toPath();
    app.writeToDirectory(out, OutputMode.DexIndexed);
    Path classes = out.resolve("classes.dex");
    Path classes2 = out.resolve("classes2.dex");
    assertTrue(Files.exists(classes));
    assertTrue(Files.exists(classes2));
    checkContainsLambdaClasses(new CodeInspector(classes), A.class);
    checkContainsLambdaClasses(new CodeInspector(classes2), B.class);
  }

  private void checkContainsLambdaClasses(CodeInspector inspector, Class<?> lambdaHolder) {
    assertTrue(
        inspector.allClasses().stream()
            .anyMatch(
                clazz ->
                    clazz.isSynthesizedJavaLambdaClass()
                        && clazz
                            .getOriginalReference()
                            .equals(
                                SyntheticItemsTestUtils.syntheticLambdaClass(lambdaHolder, 0))));
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(new A().foo().get());
    }
  }

  interface Getter {
    String get();
  }

  static class A {
    Getter foo() {
      return () -> "A";
    }
  }

  static class B {
    Getter foo() {
      return () -> "B";
    }
  }
}
