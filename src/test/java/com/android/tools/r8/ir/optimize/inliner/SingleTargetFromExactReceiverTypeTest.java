// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetFromExactReceiverTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
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
        .enableAlwaysInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyOnlyCanBeInlinedHasBeenInlined)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "A.canBeInlined()",
            "A.canBeInlinedDueToAssume()",
            "B.canBeInlined()",
            "B.canBeInlinedDueToAssume()",
            "A.cannotBeInlinedDueToDynamicDispatch()",
            "A.cannotBeInlinedDueToKeepRule()");
  }

  private void verifyOnlyCanBeInlinedHasBeenInlined(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithOriginalName("canBeInlined"), not(isPresent()));
    assertThat(
        aClassSubject.uniqueMethodWithOriginalName("canBeInlinedDueToAssume"), not(isPresent()));
    assertThat(
        aClassSubject.uniqueMethodWithOriginalName("cannotBeInlinedDueToDynamicDispatch"),
        isPresent());
    assertThat(
        aClassSubject.uniqueMethodWithOriginalName("cannotBeInlinedDueToKeepRule"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithOriginalName("canBeInlined"), not(isPresent()));
    assertThat(
        bClassSubject.uniqueMethodWithOriginalName("canBeInlinedDueToAssume"), not(isPresent()));
    assertThat(
        bClassSubject.uniqueMethodWithOriginalName("cannotBeInlinedDueToDynamicDispatch"),
        isPresent());
    assertThat(
        bClassSubject.uniqueMethodWithOriginalName("cannotBeInlinedDueToKeepRule"), isPresent());

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    for (String expected :
        ImmutableList.of(
            "A.canBeInlined()",
            "A.canBeInlinedDueToAssume()",
            "B.canBeInlined()",
            "B.canBeInlinedDueToAssume()")) {
      assertTrue(
          mainMethodSubject
              .streamInstructions()
              .anyMatch(x -> x.isConstString(expected, JumboStringMode.ALLOW)));
    }
    assertThat(
        mainMethodSubject,
        invokesMethod(
            aClassSubject.uniqueMethodWithOriginalName("cannotBeInlinedDueToDynamicDispatch")));
    assertThat(
        mainMethodSubject,
        invokesMethod(aClassSubject.uniqueMethodWithOriginalName("cannotBeInlinedDueToKeepRule")));
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().canBeInlined();
      getInstanceOfAWithExactType().canBeInlinedDueToAssume();

      new B().canBeInlined();
      getInstanceOfBWithExactType().canBeInlinedDueToAssume();

      (System.currentTimeMillis() >= 0 ? new A() : new B()).cannotBeInlinedDueToDynamicDispatch();
      new A().cannotBeInlinedDueToKeepRule();
    }

    @NeverInline
    static A getInstanceOfAWithExactType() {
      return new A();
    }

    @NeverInline
    static A getInstanceOfBWithExactType() {
      return new B();
    }
  }

  @NeverClassInline
  static class A {

    public void canBeInlined() {
      System.out.println("A.canBeInlined()");
    }

    @AlwaysInline
    public void canBeInlinedDueToAssume() {
      System.out.println("A.canBeInlinedDueToAssume()");
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
    public void canBeInlinedDueToAssume() {
      System.out.println("B.canBeInlinedDueToAssume()");
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
