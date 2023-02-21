// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction of b/241636314. */
@RunWith(Parameterized.class)
public class ShortenLiveRangesOfInstanceGetWithPhiUserRegressionTest extends TestBase {

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
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "2");
  }

  static class Main {

    public final long index;

    Main(long index) {
      this.index = index;
    }

    public static void main(String[] args) {
      Main main = new Main(1);
      long index = 0;
      do {
        System.out.println(index); // Print the value of the phi.
        index = main.index;
        main = new Main(index + 1);
      } while (index < 3);
    }
  }
}
