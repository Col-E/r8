// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EscapeBeforeStaticPutTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EscapeBeforeStaticPutTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EscapeBeforeStaticPutTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    static String greeting;

    public static void main(String[] args) {
      greeting = "Hello world!";
      if (A.alwaysTrue) {
        // Unset `greeting` such that the test will not print "Hello world!" if `A.alwaysTrue` has
        // been propagated.
        greeting = null;
        System.out.println(A.greetings[0]);
      }
    }
  }

  static class A {

    static boolean alwaysTrue = true;
    static String[] greetings;

    static {
      String[] array = new String[] {null};
      for (int i = 0; i < 2; i++) {
        // Although this instruction is prior to the static-put instruction, it can still leak the
        // `greetings` array. Hence, this class initializer cannot be postponed, because it may
        // (and does) rely on the environment.
        leak();
        greetings = array;
      }
    }

    @NeverInline
    static void leak() {
      if (greetings != null) {
        greetings[0] = TestClass.greeting;
      }
    }
  }
}
