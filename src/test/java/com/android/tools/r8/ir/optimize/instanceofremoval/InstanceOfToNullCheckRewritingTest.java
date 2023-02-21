// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.instanceofremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceOfToNullCheckRewritingTest extends TestBase {

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
        .addKeepMainRule(Main.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(I.class), isPresent()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "false");
  }

  static class Main {

    public static void main(String[] args) {
      A nonNullA = System.currentTimeMillis() >= 0 ? new A() : null;
      A nullA = System.currentTimeMillis() < 0 ? new A() : null;
      System.out.println(nonNullA instanceof I);
      System.out.println(nullA instanceof I);
    }
  }

  @NoVerticalClassMerging
  interface I {}

  static class A implements I {}
}
