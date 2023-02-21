// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstClassComparisonTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConstClassComparisonTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConstClassComparisonTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "false", "false", "true");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject mainMethod = classSubject.mainMethod();
    assertThat(mainMethod, isPresent());
    assertTrue(mainMethod.streamInstructions().noneMatch(InstructionSubject::isConstClass));

    assertThat(inspector.clazz(A.class), not(isPresent()));
    assertThat(inspector.clazz(B.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(getA() == getA());
      System.out.println(getA() != getA());
      System.out.println(getA() == getB());
      System.out.println(getA() != getB());
    }

    static Class<?> getA() {
      return A.class;
    }

    static Class<?> getB() {
      return B.class;
    }
  }

  static class A {}

  static class B {}
}
