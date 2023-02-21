// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.interfaces;


import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdenticalFunctionalInterfacesWithIntersectionMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IdenticalFunctionalInterfacesWithIntersectionMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder ->
                testBuilder.addHorizontallyMergedClassesInspector(
                    inspector -> inspector.assertIsCompleteMergeGroup(I.class, J.class)))
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .noClassInliningOfSynthetics()
        .noInliningOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I & J", "I & J");
  }

  static class Main {

    public static void main(String[] args) {
      Object o = ((I & J) () -> System.out.println("I & J"));
      ((I) o).f();
      ((J) o).f();
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
    void f();
  }
}
