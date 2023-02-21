// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.interfaces;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassHierarchyCycleCrossGroupMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassHierarchyCycleCrossGroupMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(I.class, J.class).assertNoOtherClassesMerged())
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main implements I, J, JSub, K, KSub, L {

    public static void main(String[] args) {}
  }

  // As inputs to the interface merging policy that is supposed to avoid that merging leads to
  // cycles in the class hierarchy, we will have the merge group {I, J, K, L}. Note that the
  // classes JSub and KSub are not eligible for class merging (@NoHorizontalClassMerging).
  //
  // From this, we will form the merge group {I, J}. Note that the classes K and L are not
  // eligible for being added to {I, J}, since this would lead to a cycle in the class hierarchy:
  // I inherits from KSub, which inherits from K, and L inherits from JSub, which inherits from J.
  //
  // As a result of this, we create a new merge group {K}. Note that L is not eligible for being
  // merged into {K}, since that would also lead to a cycle in the class hierarchy. In particular,
  // if we form {K, L}, then {K, L} inherits from JSub, which inherits from {I, J}, which
  // inherits from KSub, which inherits from {K, L}.
  //
  // Therefore, none of K and L should be eligible for merging in this case.
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I extends KSub {}

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {}

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface JSub extends J {}

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface K {}

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface KSub extends K {}

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface L extends JSub {}
}
