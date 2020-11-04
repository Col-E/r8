// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class PrivateAndStaticMethodCollisionTest extends HorizontalClassMergingTestBase {

  public PrivateAndStaticMethodCollisionTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging, inspector -> inspector.assertMergedInto(B.class, A.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()", "A.bar()", "B.foo()", "B.bar()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().foo();
      new A().bar();
      new B().foo();
      new B().bar();
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    private static void foo() {
      System.out.println("A.foo()");
    }

    @NeverInline
    private void bar() {
      System.out.println("A.bar()");
    }
  }

  @NeverClassInline
  static class B {

    @NeverInline
    private void foo() {
      System.out.println("B.foo()");
    }

    @NeverInline
    private static void bar() {
      System.out.println("B.bar()");
    }
  }
}
