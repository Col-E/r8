// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

/** Regression test for b/120121170. */
public class ForceInliningWithStaticInterfaceMethodTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("A.<init>()", "I.m()", "B.<init>()");
    testForR8(Backend.DEX)
        .addInnerClasses(ForceInliningWithStaticInterfaceMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(AndroidApiLevel.M)
        .compile()
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      new B();
    }
  }

  static class A {

    public A() {
      System.out.println("A.<init>()");

      // By the time the vertical class merger runs, I.m() still exists, so the inlining oracle
      // concludes that A.<init>() is eligible for inlining. However, by the time A.<init>() will
      // be force-inlined into B.<init>(), I.m() has been rewritten as a result of interface method
      // desugaring, but the target method is not yet added to the application. Hence the inlining
      // oracle concludes that A.<init>() is not eligible for inlining, which leads to an error
      // "FORCE inlining on non-inlinable".
      I.m();
    }
  }

  static class B extends A {

    public B() {
      System.out.println("B.<init>()");
    }
  }

  interface I {

    static void m() {
      System.out.println("I.m()");
    }
  }
}
