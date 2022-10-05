// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.finalize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AllOf.allOf;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
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
public class FinalizeVirtualMethodWithSiblingTest extends TestBase {

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
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject iClassSubject = inspector.clazz(I.class);
              assertThat(iClassSubject, isPresent());
              assertThat(
                  iClassSubject.uniqueMethodWithOriginalName("m"),
                  allOf(isPresent(), not(isFinal())));

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(
                  aClassSubject.uniqueMethodWithOriginalName("m"), allOf(isPresent(), isFinal()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.m()", "C.m()");
  }

  static class Main {

    public static void main(String[] args) {
      test(new B());
      test(new C());
    }

    static void test(I i) {
      i.m();
    }
  }

  interface I {

    void m();
  }

  @NoVerticalClassMerging
  static class A {

    // Should be made final.
    @NeverInline
    public void m() {
      System.out.println("A.m()");
    }
  }

  @NoHorizontalClassMerging
  static class B extends A implements I {}

  // To prevent the call i.m() from being devirtualized.
  @NoHorizontalClassMerging
  static class C implements I {
    @Override
    public void m() {
      System.out.println("C.m()");
    }
  }
}
