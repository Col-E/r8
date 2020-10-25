// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumenosideeffectsPropagationWithSuperCallTest extends TestBase {
  private static final Class<?> MAIN = DelegatesUser.class;
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("[Base] message1", "[Base] message2", "The end");

  // With horizontal class merging enabled the method body for debug is cleared, because the
  // forwarded call to the class specific implementation has no side effects. The call to the
  // function from main persists.
  private static final String EXPECTED_OUTPUT_WITH_HORIZONTAL_CLASS_MERGING =
      StringUtils.lines("[Base] message2", "The end");

  enum TestConfig {
    SPECIFIC_RULES,
    NON_SPECIFIC_RULES_WITH_EXTENDS;

    public String getKeepRules() {
      switch (this) {
        case SPECIFIC_RULES:
          return StringUtils.lines(
              "-assumenosideeffects class **.*Sub* {",
              "  *** debug(...);",
              "}"
          );
        case NON_SPECIFIC_RULES_WITH_EXTENDS:
          return StringUtils.lines(
              "-assumenosideeffects class * extends " + BaseClass.class.getTypeName() + " {",
              "  *** debug(...);",
              "}"
          );
        default:
          throw new Unreachable();
      }
    }

    public String expectedOutput(TestParameters parameters) {
      switch (this) {
        case SPECIFIC_RULES:
        case NON_SPECIFIC_RULES_WITH_EXTENDS:
          return parameters.isCfRuntime()
              ? EXPECTED_OUTPUT
              : EXPECTED_OUTPUT_WITH_HORIZONTAL_CLASS_MERGING;
        default:
          throw new Unreachable();
      }
    }
  }

  private final TestParameters parameters;
  private final TestConfig config;

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), TestConfig.values());
  }

  public AssumenosideeffectsPropagationWithSuperCallTest(
      TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumenosideeffectsPropagationWithSuperCallTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput(parameters));
  }

  static class BaseClass {
    void debug(String message) {
      System.out.println("[Base] " + message);
    }
  }

  static class DelegateSub1 extends BaseClass {
    // TODO(b/133208961): Should behave same with and without the following definition.
    @Override
    public void debug(String message) {
      super.debug(message);
    }
  }

  static class DelegateSub2 extends BaseClass {
    @Override
    public void debug(String message) {
      System.out.println("[Sub2] " + message);
    }
  }

  static class DelegatesUser {
    static BaseClass createBase() {
      return System.currentTimeMillis() > 0 ? new DelegateSub1() : new DelegateSub2();
    }

    public static void main(String... args) {
      BaseClass instance = createBase();
      instance.debug("message1");
      if (System.currentTimeMillis() > 0) {
        instance = new BaseClass();
      }
      instance.debug("message2");
      System.out.println("The end");
    }
  }
}
