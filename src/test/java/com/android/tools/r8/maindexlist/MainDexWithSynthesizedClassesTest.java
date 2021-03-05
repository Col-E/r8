// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
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
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      D8TestCompileResult compileResult =
          testForD8()
              .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
              .addMainDexKeepClassAndMemberRules(TestClass.class)
              .setMinApi(parameters.getApiLevel())
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
            .setMinApi(parameters.getApiLevel())
            .setIntermediate(true)
            .compile();
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(intermediateResult.writeToZip())
            .addMainDexKeepClassAndMemberRules(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile();
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
  public void testDeprecatedSyntheticsFromMainDexListD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult compileResult =
        testForD8()
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .addMainDexListClasses(TestClass.class, A.class)
            .setMinApi(parameters.getApiLevel())
            .compile();
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
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(o -> o.minimalMainDex = true)
            .addMainDexListClasses(TestClass.class, A.class)
            .noMinification()
            .noTreeShaking()
            .compile();
    checkCompilationResult(compileResult, compileResult.app);
  }

  private void checkCompilationResult(D8TestCompileResult compileResult) throws Exception {
    checkCompilationResult(compileResult, compileResult.app);
  }

  private void checkCompilationResult(TestCompileResult compileResult, AndroidApp app)
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
