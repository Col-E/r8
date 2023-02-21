// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class VirtualMethodMergingOfFinalAndNonFinalMethodTest
    extends HorizontalClassMergingTestBase {

  public VirtualMethodMergingOfFinalAndNonFinalMethodTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isAbsent());
              assertThat(inspector.clazz(C.class), isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A.foo()", "B.foo()", "C.foo()");
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    public final void foo() {
      System.out.println("A.foo()");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class B {

    @NeverInline
    public void foo() {
      System.out.println("B.foo()");
    }
  }

  @NeverClassInline
  public static class C extends B {

    @NeverInline
    @Override
    public void foo() {
      System.out.println("C.foo()");
    }
  }

  public static class TestClass {
    public static void main(String[] args) {
      new A().foo();
      new B().foo();
      new C().foo();
    }
  }
}
