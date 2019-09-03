// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetFromExactReceiverTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public SingleTargetFromExactReceiverTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingleTargetFromExactReceiverTypeTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keepclassmembers class " + A.class.getTypeName() + " {",
            "  void cannotBeInlinedDueToKeepRule();",
            "}")
        .enableClassInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyOnlyCanBeInlinedHasBeenInlined)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "A.canBeInlined()",
            "B.canBeInlined()",
            "A.cannotBeInlinedDueToDynamicDispatch()",
            "A.cannotBeInlinedDueToKeepRule()");
  }

  private void verifyOnlyCanBeInlinedHasBeenInlined(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("canBeInlined"), not(isPresent()));
    assertThat(
        aClassSubject.uniqueMethodWithName("cannotBeInlinedDueToDynamicDispatch"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("cannotBeInlinedDueToKeepRule"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithName("canBeInlined"), not(isPresent()));
    assertThat(
        bClassSubject.uniqueMethodWithName("cannotBeInlinedDueToDynamicDispatch"), isPresent());
    assertThat(bClassSubject.uniqueMethodWithName("cannotBeInlinedDueToKeepRule"), isPresent());

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(
        mainMethodSubject
            .streamInstructions()
            .anyMatch(x -> x.isConstString("A.canBeInlined()", JumboStringMode.ALLOW)));
    assertThat(
        mainMethodSubject,
        invokesMethod(aClassSubject.uniqueMethodWithName("cannotBeInlinedDueToDynamicDispatch")));
    assertThat(
        mainMethodSubject,
        invokesMethod(aClassSubject.uniqueMethodWithName("cannotBeInlinedDueToKeepRule")));
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().canBeInlined();
      new B().canBeInlined();
      (System.currentTimeMillis() >= 0 ? new A() : new B()).cannotBeInlinedDueToDynamicDispatch();
      new A().cannotBeInlinedDueToKeepRule();
    }
  }

  @NeverClassInline
  static class A {

    public void canBeInlined() {
      System.out.println("A.canBeInlined()");
    }

    public void cannotBeInlinedDueToDynamicDispatch() {
      System.out.println("A.cannotBeInlinedDueToDynamicDispatch()");
    }

    public void cannotBeInlinedDueToKeepRule() {
      System.out.println("A.cannotBeInlinedDueToKeepRule()");
    }
  }

  static class B extends A {

    @Override
    public void canBeInlined() {
      System.out.println("B.canBeInlined()");
    }

    @Override
    public void cannotBeInlinedDueToDynamicDispatch() {
      System.out.println("B.cannotBeInlinedDueToDynamicDispatch()");
    }

    @Override
    public void cannotBeInlinedDueToKeepRule() {
      System.out.println("B.cannotBeInlinedDueToKeepRule()");
    }
  }
}
