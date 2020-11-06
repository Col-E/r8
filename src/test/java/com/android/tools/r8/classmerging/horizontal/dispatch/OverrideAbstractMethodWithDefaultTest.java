// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import org.junit.Test;

public class OverrideAbstractMethodWithDefaultTest extends HorizontalClassMergingTestBase {
  public OverrideAbstractMethodWithDefaultTest(
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
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging, inspector -> inspector.assertNoClassesMerged())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("J", "B2")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), isPresent());
              assertThat(codeInspector.clazz(J.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B1.class), isPresent());
              assertThat(codeInspector.clazz(B2.class), isPresent());
              assertThat(codeInspector.clazz(C1.class), isPresent());
              assertThat(codeInspector.clazz(C2.class), isPresent());
            });
  }

  @NoVerticalClassMerging
  interface I {
    void m();
  }

  @NoVerticalClassMerging
  interface J extends I {
    default void m() {
      System.out.println("J");
    }
  }

  abstract static class A implements I {}

  @NoVerticalClassMerging
  abstract static class B1 extends A {}

  @NoVerticalClassMerging
  abstract static class B2 extends A {
    @Override
    @NeverInline
    public void m() {
      System.out.println("B2");
    }
  }

  static class C1 extends B1 implements J {}

  static class C2 extends B2 {}

  static class Main {
    @NeverInline
    public static void doI(I i) {
      i.m();
    }

    public static void main(String[] args) {
      doI(new C1());
      doI(new C2());
    }
  }
}
