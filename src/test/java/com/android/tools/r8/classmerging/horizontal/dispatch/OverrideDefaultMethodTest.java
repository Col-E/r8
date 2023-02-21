// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import org.junit.Assume;
import org.junit.Test;

public class OverrideDefaultMethodTest extends HorizontalClassMergingTestBase {
  public OverrideDefaultMethodTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue("b/197494749", parameters.canUseDefaultAndStaticInterfaceMethods());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                inspector.assertNoClassesMerged();
              } else {
                inspector
                    .assertClassesNotMerged(A.class, B.class)
                    .assertIsCompleteMergeGroup(I.class, J.class)
                    .assertIsCompleteMergeGroup(
                        SyntheticItemsTestUtils.syntheticCompanionClass(I.class),
                        SyntheticItemsTestUtils.syntheticCompanionClass(J.class))
                    .assertNoOtherClassesMerged();
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "B", "J")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), isPresent());
              assertThat(
                  codeInspector.clazz(J.class),
                  onlyIf(parameters.canUseDefaultAndStaticInterfaceMethods(), isPresent()));
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  interface I {
    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  @NeverClassInline
  public static class A implements I {}

  @NeverClassInline
  public static class B implements I {
    @NeverInline
    @Override
    public void m() {
      System.out.println("B");
    }
  }

  @NoVerticalClassMerging
  interface J extends I {
    default void m() {
      System.out.println("J");
    }
  }

  @NeverClassInline
  public static class C extends A implements J {}

  public static class Main {
    @NeverInline
    public static void doI(I i) {
      i.m();
    }

    public static void main(String[] args) {
      doI(new A());
      doI(new B());
      doI(new C());
    }
  }
}
