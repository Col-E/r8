// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B138912149 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B138912149.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("The end")
        .inspect(
            codeInspector -> {
              // All <clinit>s are simplified and shrunk.
              codeInspector.forAllClasses(
                  classSubject -> {
                    assertThat(classSubject.clinit(), not(isPresent()));
                  });
            });
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
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
      if (C.alwaysNull != null) {
        System.out.println("Dead code 3");
      }
      if (!N.name.endsWith("N")) {
        System.out.println("Dead code: " + N.name);
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

  static class C {
    static Object alwaysNull;

    static {
      alwaysNull = C.class;
      // Either keep this static-put or remove both static-put's and put `null` to `alwaysNull`.
      alwaysNull = null;
    }
  }

  static class N {
    static String name;

    static {
      name = "dummy";
      // Either keep this static-put or remove both static-put's and put the name, ...$N, to `name`.
      name = N.class.getName();
      // Note that we can't use other kinds of names, e.g., simple name, because the enclosing class
      // is not provided as a program class, hence all attributes related to inner-name computation
      // is gone in this test setting.
    }
  }
}
