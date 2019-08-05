// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B138912149 extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public B138912149(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B138912149.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getRuntime())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/138912149): Should be "The end".
        .assertSuccessWithOutputLines("Dead code 2", "The end");
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("The end");
  }

  static class TestClass {

    public static void main(String... args) {
      if (A.alwaysFalse) {
        System.out.println("Dead code 1");
      }
      if (B.alwaysFalse || !B.alwaysTrue) {
        System.out.println("Dead code 2");
      }
      System.out.println("The end");
    }
  }

  static class A {
    static boolean alwaysFalse;

    static {
      alwaysFalse = false;
    }
  }

  static class B extends A {
    static boolean alwaysTrue;

    static {
      alwaysTrue = alwaysFalse;
      // Either keep this static-put or remove both static-put's and put `true` to `alwaysTrue`.
      alwaysTrue = true;
    }
  }
}
