// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantiatedLambdaReceiverTest extends TestBase {

  private static final String expectedOutput = "In C.m()";
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public InstantiatedLambdaReceiverTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void jvmTest() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void r8Test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InstantiatedLambdaReceiverTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  interface I {
    void m();
  }

  interface II extends I {}

  static class C implements I {

    @Override
    public void m() {
      System.out.print("In C.m()");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      I i = new C();
      II x = i::m; // This should mark II as being instantiated!
      x.m();
    }
  }
}
