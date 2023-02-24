// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.google.common.base.Predicates.alwaysTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.m()", "In B.m()");

    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(InterfaceMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject interfaceSubject = inspector.clazz(I.class);
    assertThat(interfaceSubject, isPresent());

    MethodSubject interfaceMethodSubject = interfaceSubject.uniqueMethodThatMatches(alwaysTrue());
    assertThat(interfaceMethodSubject, isPresent());

    for (Class<?> clazz : ImmutableList.of(A.class, B.class)) {
      ClassSubject classSubject = inspector.clazz(clazz);
      assertThat(classSubject, isPresent());
      assertThat(
          classSubject.method(interfaceMethodSubject.getProgramMethod().getMethodReference()),
          isPresent());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new A());
      test(new B());
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

  @NeverClassInline
  static class A implements I {

    @NeverInline
    @Override
    public Uninstantiated m() {
      System.out.println("In A.m()");
      return null;
    }
  }

  // The purpose of this class is merely to avoid that the invoke-interface instruction in
  // TestClass.test() gets devirtualized to an invoke-virtual instruction. Otherwise the method
  // I.m() would not be present in the output.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements I {

    @Override
    public Uninstantiated m() {
      System.out.println("In B.m()");
      return null;
    }
  }

  static class Uninstantiated {}
}
