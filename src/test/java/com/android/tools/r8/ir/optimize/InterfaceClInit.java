// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceClInit extends TestBase {
  static String EXPECTED= "i = 42";
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InterfaceClInit(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, InterfaceWithStaticBlock.class, UsedFromStatic.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class, InterfaceWithStaticBlock.class, UsedFromStatic.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}

class TestClass implements InterfaceWithStaticBlock {
  @Override
  public void foobar() { }

  public static void main(String[] args) {
    UsedFromStatic.i = 42;
    System.out.println("i = " + InterfaceWithStaticBlock.i);
  }
}

interface InterfaceWithStaticBlock {
  int i = UsedFromStatic.i;
  void foobar();
}

class UsedFromStatic {
  public static int i = 60;
}
