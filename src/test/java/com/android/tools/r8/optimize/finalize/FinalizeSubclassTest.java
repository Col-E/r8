// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.finalize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AllOf.allOf;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FinalizeSubclassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, allOf(isPresent(), not(isFinal())));

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, allOf(isPresent(), not(isFinal())));

              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, allOf(isPresent(), isFinal()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.m()", "B.m()", "C.m()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      new B().m();
      new C().m();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    void m() {
      System.out.println("A.m()");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class B extends A {

    @NeverInline
    void m() {
      System.out.println("B.m()");
    }
  }

  // Should become final.
  @NeverClassInline
  static class C extends B {

    @NeverInline
    void m() {
      System.out.println("C.m()");
    }
  }
}
