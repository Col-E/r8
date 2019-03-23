// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import org.junit.Test;

// Regression test for b/128987064
public class DoubleInliningInvokeSuperTest extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(DoubleInliningInvokeSuperTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keepclassmembers class * { void fooCaller(...); }")
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .run(TestClass.class)
        .assertSuccess();
  }

  @NeverClassInline
  @NeverMerge
  static class A {
    int x;
    @NeverInline
    A foo(int x) {
      this.x = x;
      return this;
    }
  }

  @NeverClassInline
  @NeverMerge
  static class B extends A {
    // B#foo is invoked twice by other wrappers in the same class.
    @Override
    B foo(int x) {
      // this invoke-super should not be inlined to the class outside of the class hierarchy.
      super.foo(x);
      return this;
    }

    // place-holder to make B#foo to be a double-inline selected target.
    B fooWrapper(Integer x) {
      int y = System.currentTimeMillis() > 0 ? x * 2 : x;
      return foo(y);
    }

    // Another B#foo caller.
    B anotherWrapper(Integer x) {
      int y = System.currentTimeMillis() > 0 ? x * 2 : x;
      // invoke-super in B#foo is inlined here during double-inlining.
      return foo(y);
    }
  }

  static class TestClass {
    // place-holder to make B#fooWrapper live. This one is force kept.
    static void fooCaller(B b) {
      System.out.println(b.fooWrapper(8).x);
    }

    public static void main(String[] args) {
      B instance = new B();
      // By invoking B#anotherWrapper twice, `main` is regarded as double-inline caller.
      // Due to the name order, B#fooWrapper is processed first, and invoke-super is moved.
      // Its inlining constraints aren't updated yet since inliner does not finish processing all
      // the methods in the double-inline pool. Now, when processing `main`, invoke-super in
      // B#anotherWrapper (which is inlined from B#foo) is flown to here, resulting in illegal
      // invoke-super at the 2nd tree shaking phase.
      System.out.println(instance.anotherWrapper(8).x);
      System.out.println(instance.anotherWrapper(8).x);
    }
  }

}
