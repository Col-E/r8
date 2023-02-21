// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberValuePropagationWithClassInitializationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberValuePropagationWithClassInitializationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(MemberValuePropagationWithClassInitializationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A!", "B!");
  }

  private void inspect(CodeInspector inspector) {
    // A.field is present.
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    FieldSubject fieldSubject = aClassSubject.uniqueFieldWithOriginalName("field");
    assertThat(fieldSubject, not(isPresent()));

    FieldSubject clinitFieldSubject = aClassSubject.uniqueFieldWithOriginalName("$r8$clinit");
    assertThat(clinitFieldSubject, isPresent());

    // B.method() is present.
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    MethodSubject methodSubject = bClassSubject.uniqueMethodWithOriginalName("method");
    assertThat(methodSubject, not(isPresent()));

    // TestClass.missingFieldValuePropagation() and TestClass.missingMethodValuePropagation() are
    // absent.
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("missingFieldValuePropagation"),
        not(isPresent()));
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("missingMethodValuePropagation"),
        not(isPresent()));

    // TestClass.main() still accesses A.field and invokes B.method().
    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(
        mainMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .anyMatch(x -> x.getField() == clinitFieldSubject.getField().getReference()));
  }

  static class TestClass {

    public static void main(String[] args) {
      if (A.field) {
        missingFieldValuePropagation();
      }
      if (B.method()) {
        missingMethodValuePropagation();
      }
    }

    @NeverInline
    static void missingFieldValuePropagation() {
      System.out.println("Missing field value propagation!");
    }

    @NeverInline
    static void missingMethodValuePropagation() {
      System.out.println("Missing method value propagation!");
    }
  }

  static class A {

    static boolean field = false;

    static {
      System.out.println("A!");
    }
  }

  static class B {

    static {
      System.out.println("B!");
    }

    static boolean method() {
      return false;
    }
  }
}
