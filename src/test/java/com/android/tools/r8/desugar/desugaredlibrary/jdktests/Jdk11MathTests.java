// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11MathTests extends TestBase {

  private static final String DIVMOD = "DivModTests";
  private static final String EXACTARITH = "ExactArithTests";

  // Build test constants.
  private static final Path JDK_11_MATH_TESTS_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_CLASSES_DIR + "Math");
  private static final Path JDK_11_STRICT_MATH_TESTS_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_CLASSES_DIR + "StrictMath");
  private static final Path[] JDK_11_MATH_TEST_CLASS_FILES =
      new Path[] {
        JDK_11_MATH_TESTS_DIR.resolve(DIVMOD + CLASS_EXTENSION),
        JDK_11_MATH_TESTS_DIR.resolve(EXACTARITH + CLASS_EXTENSION)
      };
  private static final Path[] JDK_11_STRICT_MATH_TEST_CLASS_FILES =
      new Path[] {JDK_11_STRICT_MATH_TESTS_DIR.resolve(EXACTARITH + CLASS_EXTENSION)};

  // JDK 11 test constants.
  private static final Path JDK_11_MATH_JAVA_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/lang/Math");
  private static final Path JDK_11_STRICT_MATH_JAVA_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/lang/StrictMath");
  private static final Path[] JDK_11_MATH_JAVA_FILES =
      new Path[] {
        JDK_11_MATH_JAVA_DIR.resolve(DIVMOD + JAVA_EXTENSION),
        JDK_11_MATH_JAVA_DIR.resolve(EXACTARITH + JAVA_EXTENSION)
      };
  private static final Path[] JDK_11_STRICT_MATH_JAVA_FILES =
      new Path[] {JDK_11_STRICT_MATH_JAVA_DIR.resolve(EXACTARITH + JAVA_EXTENSION)};

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withCfRuntime(CfVm.JDK11).build();
  }

  public Jdk11MathTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileMathClasses() throws Exception {
    File mathClassesDir = new File(JDK_11_MATH_TESTS_DIR.toString());
    assert mathClassesDir.exists() || mathClassesDir.mkdirs();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(JDK_11_MATH_JAVA_FILES)
        .setOutputPath(JDK_11_MATH_TESTS_DIR)
        .compile();

    File strictMathClassesDir = new File(JDK_11_STRICT_MATH_TESTS_DIR.toString());
    assert strictMathClassesDir.exists() || strictMathClassesDir.mkdirs();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(JDK_11_STRICT_MATH_JAVA_FILES)
        .setOutputPath(JDK_11_STRICT_MATH_TESTS_DIR)
        .compile();
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
