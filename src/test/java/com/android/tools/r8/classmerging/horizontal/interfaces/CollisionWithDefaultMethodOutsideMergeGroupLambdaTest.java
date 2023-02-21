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
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CollisionWithDefaultMethodOutsideMergeGroupLambdaTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CollisionWithDefaultMethodOutsideMergeGroupLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // I and J are not eligible for merging, since the lambda that implements I & J inherits a
        // default m() method from K, which is also on J.
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.isCfRuntime()) {
                inspector.assertIsCompleteMergeGroup(I.class, J.class);
              } else {
                inspector.assertNoClassesMerged();
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
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                assertThat(aClassSubject, isPresent());
                if (parameters.isCfRuntime()) {
                  assertThat(aClassSubject, isImplementing(inspector.clazz(I.class)));
                } else {
                  assertThat(aClassSubject, isImplementing(inspector.clazz(J.class)));
                }
              } else {
                assertThat(aClassSubject, isAbsent());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/173990042): Should succeed with "K", "J".
        .applyIf(
            parameters.isCfRuntime(),
            builder ->
                builder.assertFailureWithErrorThatThrows(
                    parameters.isCfRuntime()
                            && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)
                        ? AbstractMethodError.class
                        : IncompatibleClassChangeError.class),
            builder -> builder.assertSuccessWithOutputLines("K", "J"));
  }

  static class Main {

    public static void main(String[] args) {
      ((I & K)
              () -> {
                throw new RuntimeException();
              })
          .m();
      new A().m();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {
    void f();
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
  static class A implements J {}
}
