// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PrunedClassNameComparisonTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PrunedClassNameComparisonTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(PrunedClassNameComparisonTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
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
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      if (get()
          .getClass()
          .getName()
          .equals("com.android.tools.r8.ir.optimize.library.PrunedClassNameComparisonTest$C")) {
        dead();
      } else {
        live();
      }
    }

    static I get() {
      return System.currentTimeMillis() >= 0 ? new A() : new B();
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

  interface I {}

  static class A implements I {}

  static class B implements I {}

  static class C implements I {}
}
