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
  private static final String JVM_OUTPUT = StringUtils.lines(
      "[Base] message1",
      "The end"
  );
  private static final String OUTPUT_WITHOUT_MESSAGES = StringUtils.lines(
      "The end"
  );

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

    public String expectedOutput() {
      switch (this) {
        case SPECIFIC_RULES:
        case NON_SPECIFIC_RULES_WITH_EXTENDS:
          return JVM_OUTPUT;
        default:
          throw new Unreachable();
      }
    }
  }

  private final TestParameters parameters;
  private final TestConfig config;

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimes().build(), TestConfig.values());
  }

  public AssumenosideeffectsPropagationWithSuperCallTest(
      TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Test
  public void testR8() throws Exception {
    expectThrowsWithHorizontalClassMerging();
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumenosideeffectsPropagationWithSuperCallTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput());
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
      System.out.println("The end");
    }
  }
}
