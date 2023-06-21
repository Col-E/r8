// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import org.junit.Test;

public class OverrideDefaultOnSuperMethodTest extends HorizontalClassMergingTestBase {
  public OverrideDefaultOnSuperMethodTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                inspector.assertNoClassesMerged();
              } else {
                inspector
                    .assertIsCompleteMergeGroup(I.class, J.class)
                    .applyIf(
                        parameters.canUseDefaultAndStaticInterfaceMethods(),
                        i ->
                            i.assertClassesNotMerged(A.class, B.class)
                                .assertIsCompleteMergeGroup(
                                    SyntheticItemsTestUtils.syntheticCompanionClass(I.class),
                                    SyntheticItemsTestUtils.syntheticCompanionClass(J.class)))
                    .applyIf(
                        !parameters.canUseDefaultAndStaticInterfaceMethods(),
                        i -> i.assertClassesMerged(A.class, B.class))
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
              assertThat(codeInspector.clazz(Parent.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(
                  codeInspector.clazz(B.class),
                  onlyIf(parameters.canUseDefaultAndStaticInterfaceMethods(), isPresent()));
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NoAccessModification
  @NoVerticalClassMerging
  interface I {
    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  public static class Parent implements I {}

  @NeverClassInline
  public static class A extends Parent {}

  @NeverClassInline
  public static class B extends Parent {
    @NeverInline
    @Override
    public void m() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  @NoAccessModification
  @NoUnusedInterfaceRemoval
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
