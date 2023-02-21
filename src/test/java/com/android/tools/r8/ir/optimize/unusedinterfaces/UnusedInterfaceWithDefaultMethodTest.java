// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedinterfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
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
public class UnusedInterfaceWithDefaultMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedInterfaceWithDefaultMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedInterfaceWithDefaultMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("J.m()");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    ClassSubject jClassSubject = inspector.clazz(J.class);
    assertThat(
        jClassSubject,
        parameters.canUseDefaultAndStaticInterfaceMethods() ? isPresent() : isAbsent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    // Verify that J is not considered an unused interface, since it provides an implementation of
    // m() that happens to be used.
    assertEquals(1, aClassSubject.getDexProgramClass().interfaces.size());
    assertEquals(
        parameters.canUseDefaultAndStaticInterfaceMethods()
            ? jClassSubject.getDexProgramClass().type
            : iClassSubject.getDexProgramClass().type,
        aClassSubject.getDexProgramClass().interfaces.values[0]);
  }

  static class TestClass {

    public static void main(String[] args) {
      indirection(new A());
    }

    @NeverInline
    @NoParameterTypeStrengthening
    private static void indirection(I obj) {
      obj.m();
    }
  }

  @NoVerticalClassMerging
  interface I {

    void m();
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  interface J extends I {

    @NeverInline
    @Override
    default void m() {
      System.out.println("J.m()");
    }
  }

  @NeverClassInline
  static class A implements J {}
}
