// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/192023718. */
@RunWith(Parameterized.class)
public class OutlineInstructionWithRepeatedOperandTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.outline.minSize = 2;
              options.outline.threshold = 2;
            })
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "0");
  }

  static class Main {
    public static void main(String[] args) {
      int zero = System.currentTimeMillis() > 0 ? 0 : -1;
      System.out.println(m1(zero));
      System.out.println(m2(zero));
    }

    @NeverInline
    static int m1(int x) {
      int y = x * 42;
      return y * y * y;
    }

    @NeverInline
    static int m2(int x) {
      int y = x * 42;
      return y * y * y;
    }
  }
}
