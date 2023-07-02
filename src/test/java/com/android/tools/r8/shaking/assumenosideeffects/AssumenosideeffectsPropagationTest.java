// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumenosideeffectsPropagationTest extends TestBase {
  private static final Class<?> MAIN = SubsUser.class;

  enum TestConfig {
    SPECIFIC_RULES,
    NON_SPECIFIC_RULES_PARTIAL,
    NON_SPECIFIC_RULES_ALL;

    public String getKeepRules() {
      switch (this) {
        case SPECIFIC_RULES:
          return StringUtils.lines(
              // Intentionally miss sub types of Base2 for info().
              "-assumenosideeffects class **.*$Sub* {",
              "  void info(...); ",
              "}",
              // All debug() and verbose() should be removed.
              "-assumenosideeffects class **.*Sub* {",
              "  void debug(...);",
              "  void verbose(...);",
              "}"
          );
        case NON_SPECIFIC_RULES_PARTIAL:
          return StringUtils.lines(
              // Intentionally miss sub types of Base2 for debug().
              "-assumenosideeffects class **.*$Sub* {",
              "  void debug(...);"
              ,"}",
              // Targeting info() and verbose() in Base1, without wildcards.
              "-assumenosideeffects class * extends " + Base1.class.getTypeName() + " {",
              "  void info(...);",
              "  void verbose(...);",
              "}",
              // Targeting info() and verbose() in Base2, with wildcards.
              "-assumenosideeffects class * extends " + Base2.class.getTypeName() + " {",
              "  void *o*(java.lang.String);",
              "}"
          );
        case NON_SPECIFIC_RULES_ALL:
          return StringUtils.lines(
              "-assumenosideeffects class **.*Sub* {",
              "  void *(java.lang.String);",
              "}"
          );
        default:
          throw new Unreachable();
      }
    }

    public String expectedOutput(boolean enableHorizontalClassMerging) {
      switch (this) {
        case SPECIFIC_RULES:
          return enableHorizontalClassMerging
              ? StringUtils.lines(
                  "[Base2, info]: message08",
                  // Base2#info also has side effects.
                  "[Base2, info]: message4",
                  "The end")
              : StringUtils.lines(
                  // Itf#info has side effects due to the missing Base2.
                  "[Sub1, info]: message00",
                  "[Sub1, info]: message1",
                  "[Base2, info]: message08",
                  // Base2#info also has side effects.
                  "[Base2, info]: message4",
                  "The end");
        case NON_SPECIFIC_RULES_PARTIAL:
          return enableHorizontalClassMerging
              ? StringUtils.lines(
                  "[AnotherSub2, debug]: message08", "[AnotherSub2, debug]: message5", "The end")
              : StringUtils.lines(
                  "[Base1, debug]: message00",
                  "[Base1, debug]: message2",
                  "[AnotherSub2, debug]: message08",
                  "[AnotherSub2, debug]: message5",
                  "The end");
        case NON_SPECIFIC_RULES_ALL:
          return StringUtils.lines("The end");
        default:
          throw new Unreachable();
      }
    }
  }

  private final TestParameters parameters;
  private final TestConfig config;
  private final boolean enableHorizontalClassMerging;

  @Parameterized.Parameters(name = "{0}, config: {1}, horizontal: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        TestConfig.values(),
        BooleanUtils.values());
  }

  public AssumenosideeffectsPropagationTest(
      TestParameters parameters, TestConfig config, boolean enableHorizontalClassMerging) {
    this.parameters = parameters;
    this.config = config;
    this.enableHorizontalClassMerging = enableHorizontalClassMerging;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(config == TestConfig.SPECIFIC_RULES);
    assumeFalse(enableHorizontalClassMerging);
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(
            "[Sub1, info]: message00",
            "[Base1, debug]: message00",
            "[Sub1, verbose]: message00",
            "[Sub1, info]: message1",
            "[Base1, debug]: message2",
            "[Sub1, verbose]: message3",
            "[Base2, info]: message08",
            "[AnotherSub2, debug]: message08",
            "[AnotherSub2, verbose]: message08",
            "[Base2, info]: message4",
            "[AnotherSub2, debug]: message5",
            "[AnotherSub2, verbose]: message6",
            "The end");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumenosideeffectsPropagationTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .addOptionsModification(
            options ->
                options.horizontalClassMergerOptions().enableIf(enableHorizontalClassMerging))
        .enableInliningAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput(enableHorizontalClassMerging));
  }

  interface Itf {
    void info(String message);
    void debug(String message);
    void verbose(String message);
  }

  static abstract class Base1 implements Itf {
    public abstract void info(String message);
    public abstract void debug(String message);
    public abstract void verbose(String message);
  }

  static class Sub1 extends Base1 {
    @Override
    public void info(String message) {
      System.out.println("[Sub1, info]: " + message);
    }

    @Override
    public void debug(String message) {
      System.out.println("[Base1, debug]: " + message);
    }

    @Override
    public void verbose(String message) {
      System.out.println("[Sub1, verbose]: " + message);
    }
  }

  static class Sub2 extends Base1 {
    @Override
    public void info(String message) {
      System.out.println("[Sub2, info]: " + message);
    }

    @Override
    public void debug(String message) {
      System.out.println("[Base1, debug]: " + message);
    }

    @Override
    public void verbose(String message) {
      System.out.println("[Sub2, verbose]: " + message);
    }
  }

  static abstract class Base2 implements Itf {
    public abstract void info(String message);
    public abstract void debug(String message);
    public abstract void verbose(String message);
  }

  static class AnotherSub1 extends Base2 {
    @Override
    public void info(String message) {
      System.out.println("[Base2, info]: " + message);
    }

    @Override
    public void debug(String message) {
      System.out.println("[AnotherSub1, debug]: " + message);
    }

    @Override
    public void verbose(String message) {
      System.out.println("[AnotherSub1, verbose]: " + message);
    }
  }

  static class AnotherSub2 extends Base2 {
    @Override
    public void info(String message) {
      System.out.println("[Base2, info]: " + message);
    }

    @Override
    public void debug(String message) {
      System.out.println("[AnotherSub2, debug]: " + message);
    }

    @Override
    public void verbose(String message) {
      System.out.println("[AnotherSub2, verbose]: " + message);
    }
  }

  static class SubsUser {
    static Base1 createBase1() {
      return System.currentTimeMillis() > 0 ? new Sub1() : new Sub2();
    }

    static Base2 createBase2() {
      return System.currentTimeMillis() < 0 ? new AnotherSub1() : new AnotherSub2();
    }

    @NeverInline
    private static void testInvokeInterface(Itf itf, String message) {
      itf.info(message);
      itf.debug(message);
      itf.verbose(message);
    }

    public static void main(String... args) {
      Base1 instance1 = createBase1();
      testInvokeInterface(instance1, "message00");
      instance1.info("message1");
      instance1.debug("message2");
      instance1.verbose("message3");
      Base2 instance2 = createBase2();
      testInvokeInterface(instance2, "message08");
      instance2.info("message4");
      instance2.debug("message5");
      instance2.verbose("message6");
      System.out.println("The end");
    }
  }
}
