// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceInitializedByInvokeStaticOnSubInterfaceTest
    extends ClassMayHaveInitializationSideEffectsTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withAllApiLevelsAlsoForCf().build();
  }

  public InterfaceInitializedByInvokeStaticOnSubInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("J");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .allowStdoutMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("J");
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("J");
  }

  @Test
  public void testClassInitializationMayHaveSideEffects() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(buildInnerClasses(getClass()).build(), TestClass.class);
    assertMayHaveClassInitializationSideEffects(appView, J.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      J.greet();
    }
  }

  interface I {

    Greeter iGreeter = new Greeter("I");
  }

  interface J extends I {

    Greeter jGreeter = new Greeter("J");

    static void greet() {}
  }

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }
  }
}
