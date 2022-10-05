// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
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
public class ConstClassMemberValuePropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConstClassMemberValuePropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConstClassMemberValuePropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("deadDueToFieldValuePropagation"),
        not(isPresent()));
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("deadDueToReturnValuePropagation"),
        not(isPresent()));

    // Verify that there are no more conditional instructions.
    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isIf));
  }

  static class TestClass {

    static Class<?> INSTANCE = TestClass.class;

    public static void main(String[] args) {
      if (INSTANCE == TestClass.class) {
        System.out.print("Hello");
      } else {
        deadDueToFieldValuePropagation();
      }
      if (get() == TestClass.class) {
        System.out.println(" world!");
      } else {
        deadDueToReturnValuePropagation();
      }
    }

    @NeverInline
    static Class<?> get() {
      return TestClass.class;
    }

    @NeverInline
    static void deadDueToFieldValuePropagation() {
      throw new RuntimeException();
    }

    @NeverInline
    static void deadDueToReturnValuePropagation() {
      throw new RuntimeException();
    }
  }
}
