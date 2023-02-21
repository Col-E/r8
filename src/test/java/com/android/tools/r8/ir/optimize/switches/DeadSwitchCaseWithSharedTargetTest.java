// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DeadSwitchCaseWithSharedTargetTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DeadSwitchCaseWithSharedTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + Main.class.getTypeName() + " {",
            "  static int FIELD return 27..30;",
            "}")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("?", "?", "O", "P", "Q", "R", "?");
  }

  static class Main {

    static int FIELD;
    static boolean alwaysTrue = System.currentTimeMillis() >= 0;

    public static void main(String[] args) {
      test(25);
      test(26);
      test(27);
      test(28);
      test(29);
      test(30);
      test(31);
    }

    static void test(int i) {
      if (alwaysTrue) {
        FIELD = i;
      }
      switch (FIELD) {
        case 26:
        case 27:
          System.out.println("O");
          break;
        case 28:
          System.out.println("P");
          break;
        case 29:
          System.out.println("Q");
          break;
        case 30:
          System.out.println("R");
          break;
        default:
          System.out.println("?");
          break;
      }
    }
  }
}
