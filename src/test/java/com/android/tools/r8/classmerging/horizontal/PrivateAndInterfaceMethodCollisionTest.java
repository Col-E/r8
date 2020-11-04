// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import org.junit.Test;

public class PrivateAndInterfaceMethodCollisionTest extends HorizontalClassMergingTestBase {

  public PrivateAndInterfaceMethodCollisionTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void test() throws Exception {
    // TODO(b/167981556): Should always succeed.
    boolean expectedToSucceed =
        !enableHorizontalClassMerging
            || parameters.isCfRuntime(CfVm.JDK11)
            || parameters.isDexRuntime();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging, inspector -> inspector.assertMergedInto(B.class, A.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(expectedToSucceed, "A.foo()", "B.bar()", "J.foo()")
        .assertFailureWithErrorThatThrowsIf(!expectedToSucceed, IllegalAccessError.class);
  }

  static class Main {

    public static void main(String[] args) {
      new A().foo();
      new B().bar();
      new C().foo();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {

    @NeverInline
    default void foo() {
      System.out.println("J.foo()");
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    private void foo() {
      System.out.println("A.foo()");
    }
  }

  @NoVerticalClassMerging
  static class B {

    // Only here to make sure that B is not made abstract as a result of tree shaking.
    @NeverInline
    public void bar() {
      System.out.println("B.bar()");
    }
  }

  @NeverClassInline
  static class C extends B implements J {}
}
