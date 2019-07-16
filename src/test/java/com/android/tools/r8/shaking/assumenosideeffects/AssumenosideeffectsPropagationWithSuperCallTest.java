// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class BaseClass {
  void debug(String message) {
    System.out.println("[Base] " + message);
  }
}

class DelegateSub1 extends BaseClass {
  // TODO(b/133208961): Should behave same with and without the following definition.
  @Override
  public void debug(String message) {
    super.debug(message);
  }
}

class DelegateSub2 extends BaseClass {
  @Override
  public void debug(String message) {
    System.out.println("[Sub2] " + message);
  }
}

class DelegatesUser {
  static BaseClass createBase() {
    return System.currentTimeMillis() > 0 ? new DelegateSub1() : new DelegateSub2();
  }

  public static void main(String... args) {
    BaseClass instance = createBase();
    instance.debug("message1");
    System.out.println("The end");
  }
}

@RunWith(Parameterized.class)
public class AssumenosideeffectsPropagationWithSuperCallTest extends TestBase {
  private static final Class<?> MAIN = DelegatesUser.class;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      MAIN, BaseClass.class, DelegateSub1.class, DelegateSub2.class);

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
              "-assumenosideeffects class * extends **.BaseClass {",
              "  *** debug(...);",
              "}"
          );
        default:
          throw new Unreachable();
      }
    }

    public String expectedOutput(boolean isR8) {
      if (!isR8) {
        return OUTPUT_WITHOUT_MESSAGES;
      }
      switch (this) {
        case SPECIFIC_RULES:
        case NON_SPECIFIC_RULES_WITH_EXTENDS:
          // TODO(b/133208961): If DelegateSub1#debug is not explicitly defined, we would not
          //   propagate the rule, and thus the output would
          // return JVM_OUTPUT;
          return OUTPUT_WITHOUT_MESSAGES;
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
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput(true));
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .noMinification()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput(false));
  }
}
