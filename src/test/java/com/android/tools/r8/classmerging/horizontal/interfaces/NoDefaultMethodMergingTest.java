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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoDefaultMethodMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NoDefaultMethodMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // I and J are not eligible for merging, since they declare the same default method.
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
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
              if (!parameters.canUseDefaultAndStaticInterfaceMethods()) {
                // When desugaring, the forwarding methods to the CC.m methods will be inlined and
                // the class instances become dead code.
                assertThat(aClassSubject, isAbsent());
                assertThat(bClassSubject, isAbsent());
                return;
              }
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject, isImplementing(inspector.clazz(I.class)));

              assertThat(bClassSubject, isPresent());
              assertThat(
                  bClassSubject,
                  isImplementing(
                      inspector.clazz(
                          parameters.canUseDefaultAndStaticInterfaceMethods()
                              ? J.class
                              : I.class)));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      new B().m();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {
    @NeverInline
    @NoMethodStaticizing
    default void m() {
      System.out.println("I");
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {
    @NeverInline
    @NoMethodStaticizing
    default void m() {
      System.out.println("J");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A implements I {}

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements J {}
}
