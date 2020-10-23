// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetAfterInliningTest extends TestBase {

  private final int maxInliningDepth;
  private final TestParameters parameters;

  @Parameters(name = "{1}, max inlining depth: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(0, 1), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public SingleTargetAfterInliningTest(int maxInliningDepth, TestParameters parameters) {
    this.maxInliningDepth = maxInliningDepth;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingleTargetAfterInliningTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.applyInliningToInlinee = true;
              options.applyInliningToInlineeMaxDepth = maxInliningDepth;
            })
        .enableAlwaysInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableSideEffectAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B.foo()", "B.bar()");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    // The indirection() method should be inlined.
    assertThat(testClassSubject.uniqueMethodWithName("indirection"), not(isPresent()));

    // A.foo() should be absent if the max inlining depth is 1, because indirection() has been
    // inlined into main(), which makes A.foo() eligible for inlining into main().
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("foo"), not(isPresent()));

    // A.bar() should always be inlined because it is marked as @AlwaysInline.
    assertThat(aClassSubject.uniqueMethodWithName("bar"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      // Ensure C is instantiated, to prevent us from finding a single target in indirection().
      new C();

      indirection(new B());
    }

    private static void indirection(A obj) {
      obj.foo();
      obj.bar();
    }
  }

  abstract static class A {

    abstract void foo();

    abstract void bar();
  }

  @NeverClassInline
  static class B extends A {

    @AssumeMayHaveSideEffects // To ensure that new B() cannot be removed.
    B() {}

    @Override
    void foo() {
      System.out.println("B.foo()");
    }

    @AlwaysInline
    @Override
    void bar() {
      System.out.println("B.bar()");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class C extends A {

    @AssumeMayHaveSideEffects // To ensure that new C() cannot be removed.
    C() {}

    @Override
    void foo() {
      System.out.println("C.foo()");
    }

    @Override
    void bar() {
      System.out.println("C.bar()");
    }
  }
}
