// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;


import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class StaticAndVirtualMethodCollisionTest extends HorizontalClassMergingTestBase {

  public StaticAndVirtualMethodCollisionTest(TestParameters parameters) {
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
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()", "A.bar()", "B.foo()", "B.bar()");
  }

  static class Main {

    public static void main(String[] args) {
      A.foo();
      new A().bar();
      new B().foo();
      B.bar();
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    public static void foo() {
      System.out.println("A.foo()");
    }

    @NeverInline
    public void bar() {
      System.out.println("A.bar()");
    }
  }

  @NeverClassInline
  static class B {

    @NeverInline
    public void foo() {
      System.out.println("B.foo()");
    }

    @NeverInline
    public static void bar() {
      System.out.println("B.bar()");
    }
  }
}
