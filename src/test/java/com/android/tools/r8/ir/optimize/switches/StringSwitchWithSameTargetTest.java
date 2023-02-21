// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringSwitchWithSameTargetTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringSwitchWithSameTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              assertTrue(options.minimumStringSwitchSize >= 3);
              options.minimumStringSwitchSize = 2;
            })
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class, "Hello", "_", "world!")
        .assertSuccessWithOutput("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      for (String arg : args) {
        switch (arg) {
          case "Hello":
          case "world!":
            System.out.print(arg);
            break;
          default:
            System.out.print(" ");
            break;
        }
      }
    }
  }
}
