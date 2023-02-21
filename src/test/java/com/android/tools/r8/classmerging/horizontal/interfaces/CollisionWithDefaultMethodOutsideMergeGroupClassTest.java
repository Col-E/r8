// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.interfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isImplementing;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CollisionWithDefaultMethodOutsideMergeGroupClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CollisionWithDefaultMethodOutsideMergeGroupClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Assume.assumeTrue("b/197494749", parameters.canUseDefaultAndStaticInterfaceMethods());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // I and J are not eligible for merging, since class A (implements I) inherits a default m()
        // method from K, which is also on J.
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                inspector.assertNoClassesMerged();
              } else {
                // J is removed as part of desugaring. This enables merging of its CC class.
                inspector.assertIsCompleteMergeGroup(
                    SyntheticItemsTestUtils.syntheticCompanionClass(J.class),
                    SyntheticItemsTestUtils.syntheticCompanionClass(K.class));
              }
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              ClassSubject bClassSubject = inspector.clazz(B.class);
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                assertThat(aClassSubject, isPresent());
                assertThat(aClassSubject, isImplementing(inspector.clazz(I.class)));
                assertThat(aClassSubject, isImplementing(inspector.clazz(K.class)));
                assertThat(bClassSubject, isPresent());
                assertThat(bClassSubject, isImplementing(inspector.clazz(J.class)));
              } else {
                // When desugaring the calls in main will directly target the CC classes.
                assertThat(aClassSubject, isAbsent());
                assertThat(bClassSubject, isAbsent());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("K", "J");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      new B().m();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {}

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {
    @NeverInline
    @NoMethodStaticizing
    default void m() {
      System.out.println("J");
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface K {
    @NeverInline
    @NoMethodStaticizing
    default void m() {
      System.out.println("K");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A implements I, K {}

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements J {}
}
