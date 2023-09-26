// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import com.android.tools.r8.ReprocessMethod;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodResolutionOptimizationInfoWithRuntimeGeneratedLambdaTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableReprocessMethodAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    @ReprocessMethod
    public static void main(String[] args) {
      I a = new A();
      I b = () -> ", world!";
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      I aInDisguise = alwaysTrue ? a : b;
      I bInDisguise = alwaysTrue ? b : a;
      System.out.print(aInDisguise.m());
      System.out.println(bInDisguise.m());
    }
  }

  interface I {

    String m();
  }

  static class A implements I {

    @Override
    public String m() {
      return "Hello";
    }
  }
}
