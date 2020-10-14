// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class MergeNonFinalAndFinalClassTest extends HorizontalClassMergingTestBase {
  public MergeNonFinalAndFinalClassTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging, inspector -> inspector.assertMergedInto(B.class, A.class))
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class), notIf(isFinal(), enableHorizontalClassMerging)))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "b", "c");
  }

  public static class Main {
    public static void main(String[] args) {
      new A();
      new B();
      new C();
    }
  }

  @NeverClassInline
  public static final class A {
    public A() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class B {
    public B() {
      System.out.println("b");
    }
  }

  @NeverClassInline
  public static class C extends B {
    public C() {
      System.out.println("c");
    }
  }
}
