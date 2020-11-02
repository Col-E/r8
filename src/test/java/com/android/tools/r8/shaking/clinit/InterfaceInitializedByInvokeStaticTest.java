// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
        .applyIf(
            DesugarTestConfiguration::isJavac,
            runResult -> runResult.assertSuccessWithOutputLines("I"))
        .applyIf(
            DesugarTestConfiguration::isNotJavac,
            runResult -> {
              if (parameters
                  .getApiLevel()
                  .isGreaterThanOrEqualTo(apiLevelWithStaticInterfaceMethodsSupport())) {
                runResult.assertSuccessWithOutputLines("I");
              } else {
                // TODO(b/172050082): Calling greet() on the companion class of I should trigger I's
                //  class initializer.
                runResult.assertSuccessWithEmptyOutput();
              }
            });
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
        .assertSuccessWithOutputLines("I");
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("I");
  }

  @Test
  public void testClassInitializationMayHaveSideEffects() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(buildInnerClasses(getClass()).build(), TestClass.class);
    assertMayHaveClassInitializationSideEffects(appView, I.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      I.greet();
    }
  }

  interface I {

    Greeter greeter = new Greeter("I");

    static void greet() {}
  }

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }
  }
}
