// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.testing;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JvmVmArgumentsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public JvmVmArgumentsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testArguments() throws Exception {
    testForJvm(parameters)
        .addTestClasspath()
        .addVmArguments("-ea")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("AssertionError!", "DONE");
  }

  @Test
  public void testMultipleArguments() throws Exception {
    testForJvm(parameters)
        .addTestClasspath()
        .addVmArguments("-ea", "-da")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("DONE");

    testForJvm(parameters)
        .addTestClasspath()
        .addVmArguments("-ea")
        .addVmArguments("-da")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("DONE");
  }

  @Test
  public void testNoArguments() throws Exception {
    testForJvm(parameters)
        .addTestClasspath()
        .addVmArguments()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("DONE");
  }

  static class TestClass {
    public static void m() {
      assert false;
    }

    public static void main(String[] args) {
      try {
        m();
      } catch (AssertionError e) {
        System.out.println("AssertionError!");
      }
      System.out.println("DONE");
    }
  }
}
