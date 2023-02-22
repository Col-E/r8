// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11MathTests extends TestBase {

  private static final String DIVMOD = "DivModTests";
  private static final String EXACTARITH = "ExactArithTests";

  private static Path[] JDK_11_MATH_TEST_CLASS_FILES;
  private static Path[] JDK_11_STRICT_MATH_TEST_CLASS_FILES;

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

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().withCfRuntime(CfVm.JDK11).build();
  }

  @BeforeClass
  public static void compileMathClasses() throws Exception {
    // Build test constants.
    Path jdk11MathTestsDir = getStaticTemp().newFolder("math").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(JDK_11_MATH_JAVA_FILES)
        .setOutputPath(jdk11MathTestsDir)
        .compile();
    JDK_11_MATH_TEST_CLASS_FILES =
        new Path[] {
          jdk11MathTestsDir.resolve(DIVMOD + CLASS_EXTENSION),
          jdk11MathTestsDir.resolve(EXACTARITH + CLASS_EXTENSION)
        };

    Path jdk11StrictMathTestsDir = getStaticTemp().newFolder("strictmath").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(JDK_11_STRICT_MATH_JAVA_FILES)
        .setOutputPath(jdk11StrictMathTestsDir)
        .compile();
    JDK_11_STRICT_MATH_TEST_CLASS_FILES =
        new Path[] {jdk11StrictMathTestsDir.resolve(EXACTARITH + CLASS_EXTENSION)};
  }

  @Test
  public void testD8MathExactArith() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testD8MathDivMod() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), DIVMOD)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testD8StrictMathExactArith() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(JDK_11_STRICT_MATH_TEST_CLASS_FILES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8MathExactArith() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(DIVMOD)
        .addKeepMainRule(EXACTARITH)
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8MathDivMod() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(DIVMOD)
        .addKeepMainRule(EXACTARITH)
        .addProgramFiles(JDK_11_MATH_TEST_CLASS_FILES)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), DIVMOD)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8StrictMathExactArith() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(JDK_11_STRICT_MATH_TEST_CLASS_FILES)
        .addKeepMainRule(EXACTARITH)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), EXACTARITH)
        .assertSuccessWithOutput("");
  }
}
