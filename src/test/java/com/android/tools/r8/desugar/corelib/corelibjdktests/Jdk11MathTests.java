// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11MathTests extends TestBase {

  private static final Path JDK_11_MATH_TESTS_DIR =
      Paths.get(ToolHelper.JAVA_CLASSES_DIR + "jdk11MathTests");
  private static final Path JDK_11_STRICT_MATH_TESTS_DIR =
      Paths.get(ToolHelper.JAVA_CLASSES_DIR + "jdk11StrictMathTests");
  private static final String DIVMOD = "DivModTests";
  private static final String EXACTARITH = "ExactArithTests";
  private static final String CLASS_SUFFIX = ".class";
  private static final Path[] JDK_11_MATH_TEST_CLASS_FILES =
      new Path[] {
        JDK_11_MATH_TESTS_DIR.resolve(DIVMOD + CLASS_SUFFIX),
        JDK_11_MATH_TESTS_DIR.resolve(EXACTARITH + CLASS_SUFFIX)
      };
  private static final Path[] JDK_11_STRICT_MATH_TEST_CLASS_FILES =
      new Path[] {JDK_11_STRICT_MATH_TESTS_DIR.resolve(EXACTARITH + CLASS_SUFFIX)};

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withCfRuntime(CfVm.JDK11).build();
  }

  public Jdk11MathTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8MathExactArith() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testD8MathDivMod() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), DIVMOD)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testD8StrictMathExactArith() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(JDK_11_STRICT_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8MathExactArith() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(DIVMOD)
        .addKeepMainRule(EXACTARITH)
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8MathDivMod() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(DIVMOD)
        .addKeepMainRule(EXACTARITH)
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), DIVMOD)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8StrictMathExactArith() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(JDK_11_STRICT_MATH_TEST_CLASS_FILES)
        .addKeepMainRule(EXACTARITH)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }
}
