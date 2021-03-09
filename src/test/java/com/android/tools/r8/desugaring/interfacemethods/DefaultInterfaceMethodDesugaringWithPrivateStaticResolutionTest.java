// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodDesugaringWithPrivateStaticResolutionTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("I.m()");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultInterfaceMethodDesugaringWithPrivateStaticResolutionTest(
      TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(DefaultInterfaceMethodDesugaringWithPrivateStaticResolutionTest.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(DefaultInterfaceMethodDesugaringWithPrivateStaticResolutionTest.class)
        .addKeepAllClassesRule()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    public static void main(String[] args) {
      I b = new B();
      b.m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  static class A {

    private static void m() {
      System.out.println("A.m()");
    }
  }

  static class B extends A implements I {}
}
