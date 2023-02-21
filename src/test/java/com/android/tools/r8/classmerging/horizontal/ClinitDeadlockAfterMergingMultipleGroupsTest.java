// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.Policy;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClinitDeadlockAfterMergingMultipleGroupsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertClassesNotMerged(A1.class, A2.class)
                    .assertIsCompleteMergeGroup(B1.class, B2.class)
                    .assertIsCompleteMergeGroup(C1.class, C2.class)
                    .assertIsCompleteMergeGroup(D1.class, D2.class)
                    .assertNoOtherClassesMerged())
        .addOptionsModification(
            options -> {
              options.horizontalClassMergerOptions().setEnableClassInitializerDeadlockDetection();
              options.testing.horizontalClassMergingPolicyRewriter =
                  policies ->
                      ImmutableList.<Policy>builder()
                          .add(getPolicyForTesting())
                          .addAll(policies)
                          .build();
            })
        .setMinApi(parameters)
        .compile();
  }

  // A custom policy for splitting the merge group {A1, A2, B1, B2, C1, C2, D1, D2} into {A1, A2},
  // {B1, B2}, {C1, C2}, {D1, D2}.
  private Policy getPolicyForTesting() {
    return new MultiClassSameReferencePolicy<String>() {

      @Override
      public String getMergeKey(DexProgramClass clazz) {
        String simpleName = clazz.getSimpleName();
        String simpleNameExcludingIndex = simpleName.substring(0, simpleName.length() - 1);
        return simpleNameExcludingIndex;
      }

      @Override
      public String getName() {
        return ClinitDeadlockAfterMergingMultipleGroupsTest.class.getTypeName();
      }
    };
  }

  static class Main {

    // @Keep
    public static void thread0() {
      // Will take the followings locks in the specified order: A1, B1.
      System.out.println(A1.b1);
    }

    // @Keep
    public static void thread1() {
      // Will take the following locks in the specified order: B2, C2.
      System.out.println(B2.c2);
    }

    // @Keep
    public static void thread2() {
      // Will take the following locks in the specified order: C1, D1.
      System.out.println(C1.d1);
    }

    // @Keep
    public static void thread3() {
      // Will take the following locks in the specified order: D2, A2.
      System.out.println(D2.a2);
    }

    // @Keep
    public static void thread4() {
      System.out.println(new A1());
      System.out.println(new A2());
      System.out.println(new B1());
      System.out.println(new B2());
      System.out.println(new C1());
      System.out.println(new C2());
      System.out.println(new D1());
      System.out.println(new D2());
    }
  }

  static class A1 {

    static B1 b1 = new B1();
  }

  static class A2 {}

  static class B1 {}

  static class B2 {

    static C2 c2 = new C2();
  }

  static class C1 {

    static D1 d1 = new D1();
  }

  static class C2 {}

  static class D1 {}

  static class D2 {

    static A2 a2 = new A2();
  }
}
