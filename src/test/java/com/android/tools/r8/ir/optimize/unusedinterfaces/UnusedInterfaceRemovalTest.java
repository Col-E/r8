// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedinterfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoHorizontalClassMerging;
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
public class UnusedInterfaceRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedInterfaceRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedInterfaceRemovalTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A.foo()", "A.bar()");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    ClassSubject jClassSubject = inspector.clazz(J.class);
    assertThat(jClassSubject, isPresent());

    ClassSubject kClassSubject = inspector.clazz(K.class);
    assertThat(kClassSubject, not(isPresent()));

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertEquals(2, aClassSubject.getDexProgramClass().interfaces.size());
    assertEquals(
        aClassSubject.getDexProgramClass().interfaces.values[0],
        iClassSubject.getDexProgramClass().type);
    assertEquals(
        aClassSubject.getDexProgramClass().interfaces.values[1],
        jClassSubject.getDexProgramClass().type);
  }

  static class TestClass {

    public static void main(String[] args) {
      I obj = System.currentTimeMillis() >= 0 ? new A() : new B();
      obj.foo();
      ((J) obj).bar();
    }
  }

  @NoVerticalClassMerging
  interface I {

    void foo();
  }

  @NoVerticalClassMerging
  interface J {

    void bar();
  }

  interface K extends I, J {}

  static class A implements K {

    @Override
    public void foo() {
      System.out.println("A.foo()");
    }

    @Override
    public void bar() {
      System.out.println("A.bar()");
    }
  }

  // To prevent that we detect a single target and start inlining or rewriting the signature in the
  // invoke.
  @NoHorizontalClassMerging
  static class B implements K {

    @Override
    public void foo() {
      throw new RuntimeException();
    }

    @Override
    public void bar() {
      throw new RuntimeException();
    }
  }
}
