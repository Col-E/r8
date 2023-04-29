// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LeadingZeroPositionTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  public LeadingZeroPositionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testZeroInInput() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkZeroLineIsPresent);
  }

  @Test
  public void testZeroAfterD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkZeroLineIsPresent);
  }

  private void checkZeroLineIsPresent(CodeInspector inspector) throws Exception {
    MethodSubject method = inspector.clazz(TestClass.class).mainMethod();
    assertTrue(method.getLineNumberTable().getLines().contains(0));
  }

  private byte[] getTransformedClass() throws Exception {
    return transformer(TestClass.class)
        .setPredictiveLineNumbering(MethodPredicate.onName("main"), 0)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.nanoTime(); // line 0 - input line, so we expect to preserve it to the output.
      System.nanoTime(); // line 100
      System.nanoTime(); // line 200
      System.nanoTime(); // line 300
      System.out.println("Hello, world"); // line 400
    }
  }
}
