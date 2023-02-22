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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnvironmentDependentClassInitializerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnvironmentDependentClassInitializerTest.class)
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
      // Set the greeting, such that A.<clinit>() will set A.greetings to ["Hello world!"].
      greeting = "Hello world!";

      // Trigger A.<clinit>().
      if (A.alwaysTrue) {
        // Reset the greeting, such that the test will fail if member value propagation is applied
        // to `A.alwaysTrue`.
        greeting = null;

        // Print "Hello world!".
        System.out.println(A.greetings[0]);
      }
    }
  }

  static class A {

    static boolean alwaysTrue = true;
    static String[] greetings = new String[] {null};

    static {
      init();
    }

    @NeverInline
    static void init() {
      greetings[0] = TestClass.greeting;
    }
  }
}
