// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.fields;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldInitializedByNonConstantArgumentInSuperConstructorTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldInitializedByNonConstantArgumentInSuperConstructorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(FieldInitializedByNonConstantArgumentInSuperConstructorTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Live!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("live"), isPresent());
    // TODO(b/280275115): Constructor inlining regresses field inlining.
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("dead"),
        isPresentIf(parameters.canInitNewInstanceUsingSuperclassConstructor()));
  }

  static class TestClass {

    public static void main(String[] args) {
      if (new B(42).x == 42) {
        live();
      } else {
        dead();
      }

      if (System.currentTimeMillis() < 0) {
        // So that we can't conclude a constant value for A.x.
        System.out.println(new B(args.length));
      }
    }

    @NeverInline
    static void live() {
      System.out.println("Live!");
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }

  @NoVerticalClassMerging
  static class A {

    int x;

    A(int x) {
      this.x = x;
    }
  }

  @NeverClassInline
  static class B extends A {

    B(int x) {
      super(x);
    }
  }
}
