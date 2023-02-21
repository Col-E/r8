// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodCollisionInSubclassAfterClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                // TODO(b/229951607): Should only merge A and B when desugaring.
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/229951607): Should always succeed.
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(
                    parameters.isCfRuntime()
                            && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)
                        ? AbstractMethodError.class
                        : IncompatibleClassChangeError.class),
            runResult -> runResult.assertSuccessWithOutputLines("A", "I", "J"));
  }

  static class Main {

    public static void main(String[] args) {
      escape(new A());
      test(new ASub());
      test(new B());
    }

    // @Keep
    static void escape(Object o) {
      System.out.println(o);
    }

    // @Keep
    static void test(I i) {
      i.m();
    }

    // @Keep
    static void test(J j) {
      j.m();
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {

    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {

    @NeverInline
    default void m() {
      System.out.println("J");
    }
  }

  @NoVerticalClassMerging
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  @NoHorizontalClassMerging
  static class ASub extends A implements I {}

  static class B implements J {}
}
