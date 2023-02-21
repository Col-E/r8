// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Test if the static method of the lambda implementation (SINGLE_CALLER) is inlined into the
// interface method of the lambda class.
@RunWith(Parameterized.class)
public class DesugaredLambdaImplementationInliningTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines(
          "Running desugared stateless lambda.",
          "Running desugared stateful lambda: lambda-state.");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  final TestParameters parameters;

  public DesugaredLambdaImplementationInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    class Counter {
      private int i = 0;
    }
    Counter counter = new Counter();
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspector()
        .clazz(TestClass.class)
        .forAllMethods(
            fms -> {
              if (fms.isStatic() && !fms.getOriginalName().equals("main")) {
                ++counter.i;
              }
            });
    // On CF the lambdas remain.
    assertEquals(parameters.isDexRuntime() ? 0 : 2, counter.i);
  }

  static class TestClass {
    public static void main(String[] args) {
      Runnable runnable = () -> System.out.println("Running desugared stateless lambda.");
      runnable.run();

      String s = "lambda-state";
      runnable = () -> System.out.println("Running desugared stateful lambda: " + s + ".");
      runnable.run();
    }
  }
}
