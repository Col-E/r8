// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetAfterInliningTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public SingleTargetAfterInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingleTargetAfterInliningTest.class)
        .addKeepMainRule(TestClass.class)
        .enableClassInliningAnnotations()
        .enableSideEffectAnnotations()
        .setMinApi(parameters.getRuntime())
        .noMinification()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    // The indirection() method should be inlined.
    assertThat(testClassSubject.uniqueMethodWithName("indirection"), not(isPresent()));

    // The main() method invokes A.method().
    // TODO(b/141451716): A.method() should be inlined into main().
    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertThat(mainMethodSubject, invokesMethod(aClassSubject.uniqueMethodWithName("method")));
  }

  static class TestClass {

    public static void main(String[] args) {
      // Ensure C is instantiated, to prevent us from finding a single target in indirection().
      new C();

      indirection(new B());
    }

    private static void indirection(A obj) {
      obj.method();
    }
  }

  abstract static class A {

    abstract void method();
  }

  @NeverClassInline
  static class B extends A {

    @AssumeMayHaveSideEffects // To ensure that new B() cannot be removed.
    B() {}

    @Override
    void method() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  static class C extends A {

    @AssumeMayHaveSideEffects // To ensure that new C() cannot be removed.
    C() {}

    @Override
    void method() {
      System.out.println("C");
    }
  }
}
