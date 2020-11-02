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
public class InterfaceWithDefaultMethodInitializedByInvokeStaticOnSubClassTest
    extends ClassMayHaveInitializationSideEffectsTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceWithDefaultMethodInitializedByInvokeStaticOnSubClassTest(
      TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(
            runResult -> {
              if (parameters.isCfRuntime()
                  || parameters
                      .getApiLevel()
                      .isGreaterThanOrEqualTo(apiLevelWithStaticInterfaceMethodsSupport())) {
                runResult.assertSuccessWithOutputLines("I");
              } else {
                // On older Android runtimes there is no default interface methods and therefore the
                // semantics is different.
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
        // TODO(b/144266257): This should succeed with "I" when default interface methods are
        //  supported, but we remove the default method I.m() because it is unused, which changes
        //  the behavior.
        .assertSuccessWithEmptyOutput();
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
    assertMayHaveClassInitializationSideEffects(appView, A.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      A.greet();
    }
  }

  interface I {

    Greeter iGreeter = new Greeter("I");

    default void m() {}
  }

  static class A implements I {

    static void greet() {}
  }

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }
  }
}
