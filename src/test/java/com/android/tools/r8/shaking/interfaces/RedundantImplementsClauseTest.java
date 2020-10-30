// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.interfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantImplementsClauseTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public RedundantImplementsClauseTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B", "C");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertTrue(
        aClassSubject
            .getDexProgramClass()
            .getInterfaces()
            .contains(iClassSubject.getDexProgramClass().getType()));

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertFalse(
        bClassSubject
            .getDexProgramClass()
            .getInterfaces()
            .contains(iClassSubject.getDexProgramClass().getType()));
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new A());
      test(new B());
      test(new C());
    }

    @NeverInline
    static void test(I instance) {
      instance.m();
    }
  }

  interface I {

    void m();
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A implements I {

    @Override
    public void m() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  static class B extends A implements I {

    @Override
    public void m() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  static class C implements I {

    @Override
    public void m() {
      System.out.println("C");
    }
  }
}
