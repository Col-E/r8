// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.commonsubexpressionelimination;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TrivialPhiAfterCommonSubexpressionEliminationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TrivialPhiAfterCommonSubexpressionEliminationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(TrivialPhiAfterCommonSubexpressionEliminationTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  static class TestClass {

    public static void main(String[] args) {
      int x = args.length;
      int y = x + 42;
      int z = x + 42;
      System.out.println(System.currentTimeMillis() > 0 ? y : z);
    }
  }
}
