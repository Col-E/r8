// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.staticinterfacemethod;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MutuallyRecursiveMethodsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("42 is even? true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public MutuallyRecursiveMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(MutuallyRecursiveMethodsTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(MutuallyRecursiveMethodsTest.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface I {
    static boolean isEven(int i) {
      return i == 0 || isOdd(i - 1);
    }

    static boolean isOdd(int i) {
      return i != 0 && isEven(i - 1);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("42 is even? " + I.isEven(42));
    }
  }
}
