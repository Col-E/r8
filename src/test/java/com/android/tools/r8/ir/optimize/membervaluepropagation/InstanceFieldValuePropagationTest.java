// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InstanceFieldValuePropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InstanceFieldValuePropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InstanceFieldValuePropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(
            StringUtils.times(StringUtils.lines("A", "42", "Hello world!"), 2));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    // Verify that all instance-get instructions in testDefinitelyNotNull() has been removed by
    // member value propagation.
    MethodSubject testDefinitelyNotNullMethodSubject =
        testClassSubject.uniqueMethodWithOriginalName("testDefinitelyNotNull");
    assertThat(testDefinitelyNotNullMethodSubject, isPresent());
    assertTrue(
        testDefinitelyNotNullMethodSubject
            .streamInstructions()
            .noneMatch(InstructionSubject::isInstanceGet));
    assertTrue(
        testDefinitelyNotNullMethodSubject
            .streamInstructions()
            .noneMatch(InstructionSubject::isNewInstance));

    // Verify that all instance-get instructions in testMaybeNull() has been removed by member value
    // propagation.
    MethodSubject testMaybeNullMethodSubject =
        testClassSubject.uniqueMethodWithOriginalName("testMaybeNull");
    assertThat(testMaybeNullMethodSubject, isPresent());
    assertTrue(
        testMaybeNullMethodSubject
            .streamInstructions()
            .noneMatch(InstructionSubject::isInstanceGet));
    // TODO(b/125282093): Should be able to remove the new-instance instruction since the instance
    //  ends up being unused.
    assertTrue(
        testMaybeNullMethodSubject
            .streamInstructions()
            .anyMatch(InstructionSubject::isNewInstance));

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertTrue(aClassSubject.allInstanceFields().isEmpty());
  }

  static class TestClass {

    public static void main(String[] args) {
      testDefinitelyNotNull();
      testMaybeNull();
    }

    @NeverInline
    static void testDefinitelyNotNull() {
      A a = new A();
      System.out.println(a.e);
      System.out.println(a.i);
      System.out.println(a.s);
    }

    @NeverInline
    static void testMaybeNull() {
      A a = System.currentTimeMillis() >= 0 ? new A() : null;
      System.out.println(a.e);
      System.out.println(a.i);
      System.out.println(a.s);
    }
  }

  @NeverClassInline
  static class A {

    MyEnum e = MyEnum.A;
    int i = 42;
    String s = "Hello world!";
  }

  public enum MyEnum {
    A,
    B
  }
}
