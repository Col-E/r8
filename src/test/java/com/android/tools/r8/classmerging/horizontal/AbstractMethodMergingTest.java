// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class AbstractMethodMergingTest extends HorizontalClassMergingTestBase {

  public AbstractMethodMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class))
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "ASub1.f()", "B.f()", "A.g()", "BSub1.g()", "ASub1.h()", "BSub1.h()");
  }

  static class Main {

    public static void main(String[] args) {
      (System.currentTimeMillis() > 0 ? new ASub1() : new ASub2()).f();
      (System.currentTimeMillis() > 0 ? new BSub1() : new BSub2()).f();
      (System.currentTimeMillis() > 0 ? new ASub1() : new ASub2()).g();
      (System.currentTimeMillis() > 0 ? new BSub1() : new BSub2()).g();
      (System.currentTimeMillis() > 0 ? new ASub1() : new ASub2()).h();
      (System.currentTimeMillis() > 0 ? new BSub1() : new BSub2()).h();
    }
  }

  @NoVerticalClassMerging
  abstract static class A {

    public abstract void f();

    @NeverInline
    public void g() {
      System.out.println("A.g()");
    }

    public abstract void h();
  }

  @NoVerticalClassMerging
  abstract static class B {

    @NeverInline
    public void f() {
      System.out.println("B.f()");
    }

    public abstract void g();

    public abstract void h();
  }

  @NoHorizontalClassMerging
  static class ASub1 extends A {

    @Override
    public void f() {
      System.out.println("ASub1.f()");
    }

    @Override
    public void h() {
      System.out.println("ASub1.h()");
    }
  }

  @NoHorizontalClassMerging
  static class ASub2 extends A {

    @Override
    public void f() {
      System.out.println("ASub2.f()");
    }

    @Override
    public void h() {
      System.out.println("ASub2.h()");
    }
  }

  @NoHorizontalClassMerging
  static class BSub1 extends B {

    @Override
    public void g() {
      System.out.println("BSub1.g()");
    }

    @Override
    public void h() {
      System.out.println("BSub1.h()");
    }
  }

  @NoHorizontalClassMerging
  static class BSub2 extends B {

    @Override
    public void g() {
      System.out.println("BSub2.g()");
    }

    @Override
    public void h() {
      System.out.println("BSub2.h()");
    }
  }
}
