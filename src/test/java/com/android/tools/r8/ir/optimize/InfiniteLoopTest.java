// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InfiniteLoopTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8().addInnerClasses(getClass()).release().setMinApi(parameters).compile();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableSideEffectAnnotations()
        .setMinApi(parameters)
        .compile();
  }

  @NeverClassInline
  static class Main {

    public static void main(String[] args) {
      new Main().loopDirect();
      loopStatic();
      new Main().loopVirtual();
    }

    @AssumeMayHaveSideEffects
    @NeverInline
    private void loopDirect() {
      while (true) {}
    }

    @AssumeMayHaveSideEffects
    @NeverInline
    public static void loopStatic() {
      while (true) {}
    }

    @AssumeMayHaveSideEffects
    @NeverInline
    public void loopVirtual() {
      while (true) {}
    }
  }
}
