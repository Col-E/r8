// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * These tests document the behavior of lambdas w.r.t identity and equality.
 *
 * <p>The D8 and R8 compilers take the stance that a program should not rely on either identity or
 * equality of any lambda metafactory allocated lambda. Thus the status of these tests differ
 * between JVM, D8/CF, D8/DEX and R8 runs as the compilers may or may not share classes and
 * allocations as seen fit.
 */
@RunWith(Parameterized.class)
public class LambdaEqualityTest extends TestBase {

  static final String EXPECTED_JAVAC =
      StringUtils.lines(
          "Same method refs",
          "true",
          "true",
          "true",
          "Different method refs",
          "false",
          "false",
          "false",
          "Empty lambda",
          "false",
          "false",
          "false");

  static final String EXPECTED_D8 =
      StringUtils.lines(
          "Same method refs",
          "true",
          "true",
          "true",
          "Different method refs",
          "true", // D8 will share the class for the method references.
          "false",
          "false",
          "Empty lambda",
          "false",
          "false",
          "false");

  static final String EXPECTED_R8 =
      StringUtils.lines(
          "Same method refs",
          "true",
          "true",
          "true",
          "Different method refs",
          "true", // R8 will share the class for the method references.
          "false",
          "false",
          "Empty lambda",
          "true", // R8 will eliminate the call to the impl method thus making lambdas equal.
          "false",
          "false");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public LambdaEqualityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForRuntime(parameters)
        .addInnerClasses(LambdaEqualityTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_JAVAC);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(LambdaEqualityTest.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(LambdaEqualityTest.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addKeepMethodRules(
            Reference.methodFromMethod(
                TestClass.class.getDeclaredMethod(
                    "compare", String.class, MyInterface.class, MyInterface.class)))
        .run(parameters.getRuntime(), TestClass.class)
        // The use of invoke dynamics prohibits the optimization and sharing of lambdas in R8.
        .assertSuccessWithOutput(parameters.isCfRuntime() ? EXPECTED_JAVAC : EXPECTED_R8);
  }

  interface MyInterface {
    void foo();
  }

  static class TestClass {

    public static void compare(String msg, MyInterface i1, MyInterface i2) {
      System.out.println(msg);
      System.out.println(i1.getClass() == i2.getClass());
      System.out.println(i1 == i2);
      System.out.println(i1.equals(i2));
    }

    public static void main(String[] args) {
      MyInterface println = System.out::println;
      // These lambdas are physically the same and should remain so in all cases.
      compare("Same method refs", println, println);
      // These lambdas can be shared as they reference the same actual function.
      compare("Different method refs", println, System.out::println);
      // These lambdas cannot be shared (by D8) as javac will generate a lambda$main$X for each.
      compare("Empty lambda", () -> {}, () -> {});
    }
  }
}
