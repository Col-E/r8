// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;


import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceInitializedByInvokeStaticTest
    extends ClassMayHaveInitializationSideEffectsTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withAllApiLevelsAlsoForCf().build();
  }

  public InterfaceInitializedByInvokeStaticTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .allowStdoutMessages()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  @Test
  public void testClassInitializationMayHaveSideEffects() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildInnerClasses(getClass())
                .addLibraryFile(ToolHelper.getMostRecentAndroidJar())
                .build(),
            TestClass.class);
    assertMayHaveClassInitializationSideEffects(appView, I.class);
    assertMayHaveClassInitializationSideEffects(appView, J.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      I.greet();
      J.greet();
    }
  }

  interface I {

    Greeter greeter = new Greeter("I");

    static void greet() {}
  }

  interface J {

    long value = new Greeter("J").longValue();

    static void greet() {}
  }

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }

    long longValue() {
      return 42;
    }
  }
}
