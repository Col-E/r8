// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction for b/128917897. */
@RunWith(Parameterized.class)
public class NestedInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimes().build();
  }

  public NestedInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.m()", "In A.m()");

    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(NestedInterfaceMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .addOptionsModification(
                options -> {
                  options.testing.enableLir();
                  options.enableDevirtualization = false;
                  options.inlinerOptions().enableInliningOfInvokesWithNullableReceivers = false;
                  // The checks for I being present rely on not simple inlining.
                  options.inlinerOptions().simpleInliningInstructionLimit = 3;
                })
            .setMinApi(AndroidApiLevel.B)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject interfaceSubject = inspector.clazz(I.class);
    assertThat(interfaceSubject, isPresent());

    MethodSubject interfaceMethodSubject =
        interfaceSubject.uniqueMethodThatMatches(
            method -> method.getProgramMethod().getReturnType().isVoidType());
    assertThat(interfaceMethodSubject, isPresent());

    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());
    assertThat(
        classSubject.uniqueMethodThatMatches(
            method ->
                method.getProgramMethod().getReference().match(interfaceMethodSubject.getMethod())),
        isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new B());
      test(new C());
    }

    @NeverInline
    private static void test(I obj) {
      obj.m();
    }
  }

  @NoVerticalClassMerging
  interface I {

    Uninstantiated m();
  }

  @NoVerticalClassMerging
  interface J extends I {}

  @NeverClassInline
  static class A implements J {

    @Override
    public Uninstantiated m() {
      System.out.println("In A.m()");
      return null;
    }
  }

  @NeverClassInline
  static class B extends A {}

  // The purpose of this class is merely to avoid that the invoke-interface instruction in
  // TestClass.test() gets devirtualized to an invoke-virtual instruction. Otherwise the method
  // I.m() would not be present in the output.
  @NeverClassInline
  static class C extends A {}

  @NoHorizontalClassMerging
  static class Uninstantiated {}
}
