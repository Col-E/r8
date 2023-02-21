// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LambdaWithDefaultMethodsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::bar", "I::bar");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public LambdaWithDefaultMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, A.class, TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface I {
    void foo();

    default void bar() {
      System.out.println("I::bar");
    }
  }

  static class A implements I {

    @Override
    public void foo() {
      System.out.println("A::foo");
    }

    @Override
    public void bar() {
      System.out.println("A::bar");
    }
  }

  static class TestClass {

    public static void runDefault(I i) {
      i.bar();
    }

    public static I createLambda() {
      return () -> System.out.println("lambda::foo");
    }

    public static void main(String[] args) {
      // Target the default method, causing it to be marked reachable.
      // This is done directly in main to ensure that it is the first thing hit in tracing.
      I i = new A();
      i.bar();
      // Create a call-site instance that will need to identify the default method as live.
      // The creation is outlined to ensure that it is not hit before the method is reachable.
      runDefault(createLambda());
    }
  }
}
