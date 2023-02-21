// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringSwitchWithHashCollisionsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringSwitchWithHashCollisionsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    assertEquals("Hello world!".hashCode(), "Hello worla~".hashCode());
    assertEquals("Hello world!".hashCode(), "Hello worlb_".hashCode());
    assertNotEquals("Hello world!".hashCode(), "_".hashCode());

    testForR8(parameters.getBackend())
        .addInnerClasses(StringSwitchWithHashCollisionsTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> assertTrue(options.minimumStringSwitchSize >= 3))
        .setMinApi(parameters)
        .compile()
        .run(
            parameters.getRuntime(),
            TestClass.class,
            "Hello world!",
            "_",
            "Hello worla~",
            "_",
            "Hello worlb_",
            "_",
            "DONE")
        .assertSuccessWithOutputLines(
            "Hello world!", "Hello world, ish!", "Hello world, ish2!", "DONE");
  }

  static class TestClass {

    public static void main(String[] args) {
      for (String string : args) {
        switch (string) {
          case "Hello world!":
            System.out.print("Hello world!");
            break;

          case "Hello worla~":
            System.out.print("Hello world, ish!");
            break;

          case "Hello worlb_":
            System.out.print("Hello world, ish2!");
            break;

          case "_":
            System.out.println();
            break;

          default:
            System.out.println("DONE");
        }
      }
    }
  }
}
