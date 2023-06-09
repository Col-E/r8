// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceWithDefaultMethodNotInitializedByInvokeStaticOnSubInterfaceTest
    extends ClassMayHaveInitializationSideEffectsTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceWithDefaultMethodNotInitializedByInvokeStaticOnSubInterfaceTest(
      TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .allowStdoutMessages()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertEquals(1, inspector.allClasses().size()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testClassInitializationMayHaveSideEffects() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildInnerClasses(getClass())
                .addLibraryFile(ToolHelper.getMostRecentAndroidJar())
                .build(),
            TestClass.class);
    assertNoClassInitializationSideEffects(appView, J.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      J.greet();
    }
  }

  interface I {

    Greeter iGreeter = new Greeter("I");

    default void m() {}
  }

  interface J extends I {

    static void greet() {}
  }

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }
  }
}
