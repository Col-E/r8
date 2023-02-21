// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValuePropagationWithCatchHandlersTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ValuePropagationWithCatchHandlersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Woops!");
  }

  static class Main {

    public static void main(String[] args) {
      try {
        test();
      } catch (ExceptionInInitializerError e) {
        System.out.println("Woops!");
      }
    }

    static synchronized void test() {
      System.out.println(Greeter.getInstance());
    }
  }

  static class Greeter {

    static final Greeter INSTANCE = new Greeter();

    static {
      if (System.currentTimeMillis() > 0) {
        throw new RuntimeException();
      }
    }

    public static Greeter getInstance() {
      return INSTANCE;
    }
  }
}
