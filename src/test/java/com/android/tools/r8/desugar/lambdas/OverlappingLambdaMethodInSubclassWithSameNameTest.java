// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.lambdas.b197625454.OverlappingLambdaMethodInSubclassWithSameNameTestA;
import com.android.tools.r8.desugar.lambdas.b197625454.OverlappingLambdaMethodInSubclassWithSameNameTestB;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

// See b/197625454 for details.
@RunWith(Parameterized.class)
public class OverlappingLambdaMethodInSubclassWithSameNameTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines("Superclass lambda: Hello!", "Superclass lambda: Hello!");

  private static final Class<?> MAIN_CLASS =
      com.android.tools.r8.desugar.lambdas.b197625454.subpackage
          .OverlappingLambdaMethodInSubclassWithSameNameTestA.class;

  @Parameter() public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForRuntime(parameters)
        .addProgramClasses(
            MAIN_CLASS,
            OverlappingLambdaMethodInSubclassWithSameNameTestA.class,
            OverlappingLambdaMethodInSubclassWithSameNameTestB.class)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(
            MAIN_CLASS,
            OverlappingLambdaMethodInSubclassWithSameNameTestA.class,
            OverlappingLambdaMethodInSubclassWithSameNameTestB.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(
            MAIN_CLASS,
            OverlappingLambdaMethodInSubclassWithSameNameTestA.class,
            OverlappingLambdaMethodInSubclassWithSameNameTestB.class)
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }
}
