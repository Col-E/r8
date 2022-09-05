// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.staticinterfacemethod;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RegressionB244970402 extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  private final TestParameters parameters;

  public RegressionB244970402(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/244970402): This should not fail.
  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addMainDexRules("-keep class * { *; }")
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("null");
  }

  interface I {

    static I getInstance() {
      return null;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(I.getInstance());
    }
  }
}
