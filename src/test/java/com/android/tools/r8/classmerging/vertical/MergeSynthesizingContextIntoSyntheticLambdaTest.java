// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MergeSynthesizingContextIntoSyntheticLambdaTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MergeSynthesizingContextIntoSyntheticLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // Disable inlining to ensure that the synthetic lambdas remain in the residual
        // program.
        .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (parameters.isCfRuntime()) {
                inspector.assertNoClassesMerged();
              } else {
                inspector.assertMergedIntoSubtype(I.class);
              }
            })
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  static class Main {
    public static void main(String[] args) {
      I i = () -> System.out.println("I");
      i.f();
      i.g().h();
    }
  }

  interface I {

    void f();

    default J g() {
      // Has synthesizing context I. After vertical class merging, this synthesizing context is
      // rewritten into the synthetic lambda defined in main().
      return () -> System.out.println("J");
    }
  }

  interface J {

    void h();
  }
}
