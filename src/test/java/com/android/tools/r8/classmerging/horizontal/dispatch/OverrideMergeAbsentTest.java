// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import org.junit.Test;

public class OverrideMergeAbsentTest extends HorizontalClassMergingTestBase {
  public OverrideMergeAbsentTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                inspector.assertNoClassesMerged();
              } else {
                // When desugaring B.m is moved and A and B can be merged.
                inspector.assertIsCompleteMergeGroup(A.class, B.class);
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "A", "J")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(J.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(
                  codeInspector.clazz(B.class),
                  parameters.canUseDefaultAndStaticInterfaceMethods() ? isPresent() : isAbsent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void m() {
      System.out.println("B");
    }
  }

  @NoVerticalClassMerging
  interface J {
    @NeverInline
    default void m() {
      System.out.println("J");
    }
  }

  @NeverClassInline
  public static class C extends A implements J {}

  public static class Main {
    @NeverInline
    public static void doI(@NoParameterTypeStrengthening J i) {
      i.m();
    }

    public static void main(String[] args) {
      new A();
      new B().m();
      doI(new C());
    }
  }
}
