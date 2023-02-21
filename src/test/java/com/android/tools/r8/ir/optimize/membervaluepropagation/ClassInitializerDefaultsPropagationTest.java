// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInitializerDefaultsPropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInitializerDefaultsPropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42.42", "42.42");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    // Both fields end up being dead due to member value propagation.
    assertEquals(0, classSubject.allFields().size());

    // Verify that there are two distinct numbers in the code.
    long floatValue = Float.floatToIntBits(42.42f);
    long doubleValue = Double.doubleToLongBits(42.42d);
    assertNotEquals(floatValue, doubleValue);
    assertEquals(
        2,
        classSubject
            .mainMethod()
            .streamInstructions()
            .filter(x -> x.isConstNumber(floatValue) || x.isConstNumber(doubleValue))
            .count());
  }

  static class TestClass {

    static float f = 42.42f;
    static double d = 42.42d;

    public static void main(String[] args) {
      // Test that we correctly map from float bits to int bits during member value propagation.
      System.out.println(f);

      // Test that we correctly map from double bits to long bits during member value propagation.
      System.out.println(d);
    }
  }
}
