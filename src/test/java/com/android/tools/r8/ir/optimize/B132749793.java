// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B132749793 extends TestBase {
  static String EXPECTED = "i = 60";
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public B132749793(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Ignore("b/132749793")
  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            TestB132749793.class, InterfaceWithStaticAndDefault.class, HasStaticField.class)
        .addKeepMainRule(TestB132749793.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestB132749793.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(
            TestB132749793.class, InterfaceWithStaticAndDefault.class, HasStaticField.class)
        .run(parameters.getRuntime(), TestB132749793.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}

class TestB132749793 implements InterfaceWithStaticAndDefault {
  @Override
  public void foobar() { }

  public static void main(String[] args) {
    HasStaticField.i = 42;
    System.out.println("i = " + InterfaceWithStaticAndDefault.i);
  }
}

interface InterfaceWithStaticAndDefault {
  int i = HasStaticField.i;
  default void foo() {
    System.out.println(i);
  }
  void foobar();
}

class HasStaticField {
  public static int i = 60;
}
