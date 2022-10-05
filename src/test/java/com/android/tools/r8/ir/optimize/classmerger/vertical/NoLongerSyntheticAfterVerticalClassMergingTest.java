// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classmerger.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoLongerSyntheticAfterVerticalClassMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NoLongerSyntheticAfterVerticalClassMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class)
        .addProgramClassFileData(
            transformer(B.class).setSynthetic(B.class.getDeclaredMethod("m")).transform())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B.m()");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), not(isPresent()));

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    MethodSubject mMethodSubject = bClassSubject.uniqueMethodWithOriginalName("m");
    assertThat(mMethodSubject, isPresent());
    assertFalse(mMethodSubject.getMethod().accessFlags.isSynthetic());
  }

  static class TestClass {

    public static void main(String[] args) {
      A a = new B();
      a.m();
    }
  }

  abstract static class A {

    public abstract void m();
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    @Override
    public void m() {
      System.out.println("B.m()");
    }
  }
}
