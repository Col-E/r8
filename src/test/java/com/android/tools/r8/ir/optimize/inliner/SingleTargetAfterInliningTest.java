// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverReprocessMethod;
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
            options ->
                options.inlinerOptions().applyInliningToInlineePredicateForTesting =
                    (appView, inlinee, inliningDepth) -> inliningDepth <= maxInliningDepth)
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNeverReprocessMethodAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableSideEffectAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B.foo()", "B.bar()");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    // The indirection() method should be inlined.
    assertThat(testClassSubject.uniqueMethodWithOriginalName("indirection"), not(isPresent()));

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    // B.foo() should be absent if the max inlining depth is 1, because indirection() has been
    // inlined into main(), which makes B.foo() eligible for inlining into main().
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(
        bClassSubject.uniqueMethodWithOriginalName("foo"),
        notIf(isPresent(), maxInliningDepth == 1));

    // B.bar() should always be inlined because it is marked as @AlwaysInline.
    assertThat(
        bClassSubject.uniqueMethodWithOriginalName("bar"),
        notIf(isPresent(), maxInliningDepth == 1));

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
  }

  static class TestClass {

    @NeverReprocessMethod
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
    @NeverInline
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
    @NeverInline
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
