// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuilderWithAppendNullCharArrayTest extends TestBase {

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
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("testWithToString passed", "testWithoutToString passed");
  }

  static class Main {

    public static void main(String[] args) {
      try {
        testWithToString();
        throw new RuntimeException();
      } catch (NullPointerException e) {
        System.out.println("testWithToString passed");
      }
      try {
        testWithoutToString();
        throw new RuntimeException();
      } catch (NullPointerException e) {
        System.out.println("testWithoutToString passed");
      }
    }

    static void testWithToString() {
      char[] chars = null;
      String string = new StringBuilder().append(chars).toString();
      System.out.println(string);
    }

    static void testWithoutToString() {
      char[] chars = null;
      new StringBuilder().append(chars);
    }
  }
}
