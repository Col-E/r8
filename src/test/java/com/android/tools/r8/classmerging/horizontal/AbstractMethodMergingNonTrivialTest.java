// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class AbstractMethodMergingNonTrivialTest extends HorizontalClassMergingTestBase {

  public AbstractMethodMergingNonTrivialTest(TestParameters parameters) {
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
        .assertSuccessWithOutputLines("ASub1.f()", "B.f()", "C.f()", "A.g()", "BSub1.g()", "C.g()");
  }

  static class Main {

    public static void main(String[] args) {
      (System.currentTimeMillis() > 0 ? new ASub1() : new ASub2()).f();
      (System.currentTimeMillis() > 0 ? new BSub1() : new BSub2()).f();
      (System.currentTimeMillis() > 0 ? new CSub1() : new CSub2()).f();
      (System.currentTimeMillis() > 0 ? new ASub1() : new ASub2()).g();
      (System.currentTimeMillis() > 0 ? new BSub1() : new BSub2()).g();
      (System.currentTimeMillis() > 0 ? new CSub1() : new CSub2()).g();
    }
  }

  @NoVerticalClassMerging
  abstract static class A {

    public abstract void f();

    @NeverInline
    public void g() {
      System.out.println("A.g()");
    }
  }

  @NoVerticalClassMerging
  abstract static class B {

    @NeverInline
    public void f() {
      System.out.println("B.f()");
    }

    public abstract void g();
  }

  abstract static class C {

    @NeverInline
    public void f() {
      System.out.println("C.f()");
    }

    @NeverInline
    public void g() {
      System.out.println("C.g()");
    }
  }

  @NoHorizontalClassMerging
  static class ASub1 extends A {

    @Override
    public void f() {
      System.out.println("ASub1.f()");
    }
  }

  @NoHorizontalClassMerging
  static class ASub2 extends A {

    @Override
    public void f() {
      System.out.println("ASub2.f()");
    }
  }

  @NoHorizontalClassMerging
  static class BSub1 extends B {

    @Override
    public void g() {
      System.out.println("BSub1.g()");
    }
  }

  @NoHorizontalClassMerging
  static class BSub2 extends B {

    @Override
    public void g() {
      System.out.println("BSub2.g()");
    }
  }

  @NoHorizontalClassMerging
  static class CSub1 extends C {}

  @NoHorizontalClassMerging
  static class CSub2 extends C {}
}
