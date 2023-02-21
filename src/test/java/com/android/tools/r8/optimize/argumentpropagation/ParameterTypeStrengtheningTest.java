// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParameterTypeStrengtheningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              // Method testA(I) should be rewritten to testA(A).
              MethodSubject testAMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testA");
              assertThat(testAMethodSubject, isPresent());
              assertEquals(
                  aClassSubject.getFinalName(),
                  testAMethodSubject.getProgramMethod().getParameter(0).getTypeName());

              // Method testB(I) should be rewritten to testB(B).
              MethodSubject testBMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testB");
              assertThat(testBMethodSubject, isPresent());
              assertEquals(
                  bClassSubject.getFinalName(),
                  testBMethodSubject.getProgramMethod().getParameter(0).getTypeName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      testA(new A());
      testB(getB());
    }

    @NeverInline
    static void testA(I i) {
      i.m();
    }

    @NeverInline
    static void testB(I i) {
      i.m();
    }

    @NeverInline
    static I getB() {
      return new B();
    }
  }

  interface I {

    void m();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A implements I {

    @Override
    public void m() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements I {

    @Override
    public void m() {
      System.out.println("B");
    }
  }
}
