// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
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

@RunWith(Parameterized.class)
public class InstanceFieldValuePropagationWithMultipleInstanceInitializersTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InstanceFieldValuePropagationWithMultipleInstanceInitializersTest(
      TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InstanceFieldValuePropagationWithMultipleInstanceInitializersTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    // Verify that the instance-get instruction in main() is still present in main(), since the
    // value of `a.greeting` depends on the constructor being used.
    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(mainMethodSubject.streamInstructions().anyMatch(InstructionSubject::isInstanceGet));

    // Verify that the `greeting` field is still present.
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueFieldWithOriginalName("greeting"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() >= 0 ? new A() : new A(new Object());
      System.out.println(a.greeting);
    }
  }

  @NeverClassInline
  static class A {

    String greeting;

    A() {
      this.greeting = "Hello world!";
    }

    A(Object unused) {
      this.greeting = ":-(";
    }
  }
}
