// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

interface TestLogger {
  void info(String tag, String message);
}

@NeverClassInline
class TestLoggerImplementer implements TestLogger {

  @NeverInline
  @Override
  public void info(String tag, String message) {
    System.out.println(tag + ": " + message);
  }
}

@NeverClassInline
class AnotherImplementer implements TestLogger {

  @NeverInline
  @Override
  public void info(String tag, String message) {
    System.out.println("[" + tag + "] " + message);
  }
}

class TestClass {
  final static String TAG = TestClass.class.getSimpleName();

  @NeverInline
  private static void testInvokeInterface(TestLogger logger, String message) {
    logger.info(TAG, message);
  }

  public static void main(String... args) {
    TestLogger instance = new TestLoggerImplementer();
    // invoke-interface, but devirtualized.
    instance.info(TAG, "message1");
    // invoke-interface, can be devirtualized with refined receiver type.
    testInvokeInterface(instance, "message2");
    AnotherImplementer anotherInstance = new AnotherImplementer();
    // invoke-virtual, single call target.
    anotherInstance.info(TAG, "message3");
    System.out.println("The end");
  }
}

@RunWith(Parameterized.class)
public class AssumenosideeffectsWithMultipleTargetsTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;

  enum TestConfig {
    RULE_THAT_DIRECTLY_REFERS_CLASS,
    RULE_THAT_DIRECTLY_REFERS_INTERFACE,
    RULE_WITH_IMPLEMENTS;

    public String getKeepRule() {
      switch (this) {
        case RULE_THAT_DIRECTLY_REFERS_CLASS:
          return StringUtils.lines(
              "-assumenosideeffects class **.TestLogger {",
              "  void info(...);",
              "}");
        case RULE_THAT_DIRECTLY_REFERS_INTERFACE:
          return StringUtils.lines(
              "-assumenosideeffects interface **.TestLogger {",
              "  void info(...);",
              "}");
        case RULE_WITH_IMPLEMENTS:
          return StringUtils.lines(
              "-assumenosideeffects class * implements **.TestLogger {",
              "  void info(...);",
              "}");
      }
      throw new Unreachable();
    }

    private static final String OUTPUT_WITH_PARTIAL_INFO = StringUtils.lines(
        "TestClass: message2",
        "The end"
    );
    private static final String OUTPUT_WITHOUT_INFO = StringUtils.lines(
        "The end"
    );

    public String expectedOutput(boolean isR8) {
      if (!isR8) {
        return OUTPUT_WITHOUT_INFO;
      }
      switch (this) {
        case RULE_THAT_DIRECTLY_REFERS_CLASS:
        case RULE_THAT_DIRECTLY_REFERS_INTERFACE:
          return OUTPUT_WITHOUT_INFO;
        case RULE_WITH_IMPLEMENTS:
          return OUTPUT_WITH_PARTIAL_INFO;
        default:
          throw new Unreachable();
      }
    }

    public void inspect(CodeInspector inspector, boolean isR8) {
      ClassSubject main = inspector.clazz(MAIN);
      assertThat(main, isPresent());

      MethodSubject mainMethod = main.mainMethod();
      assertThat(mainMethod, isPresent());
      assertEquals(
          0,
          Streams.stream(mainMethod.iterateInstructions(
              i -> i.isInvoke() && i.getMethod().name.toString().equals("info"))).count());

      MethodSubject testInvokeInterface = main.uniqueMethodWithName("testInvokeInterface");
      if (isR8) {
        switch (this) {
          case RULE_THAT_DIRECTLY_REFERS_CLASS:
          case RULE_THAT_DIRECTLY_REFERS_INTERFACE:
            assertThat(testInvokeInterface, not(isPresent()));
            break;
          case RULE_WITH_IMPLEMENTS:
            assertThat(testInvokeInterface, isPresent());
            // TODO(b/130804193): upwards propagation of member rules.
            assertEquals(
                1,
                Streams.stream(testInvokeInterface.iterateInstructions(
                    i -> i.isInvoke() && i.getMethod().name.toString().equals("info"))).count());
            break;
          default:
            throw new Unreachable();
        }
      } else {
        assertThat(testInvokeInterface, not(isPresent()));
      }

      FieldSubject tag = main.uniqueFieldWithName("TAG");
      if (isR8) {
        assertThat(tag, not(isPresent()));
      } else {
        assertThat(tag, isPresent());
      }
    }
  }

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimes().build(), TestConfig.values());
  }

  private final TestParameters parameters;
  private final TestConfig config;

  public AssumenosideeffectsWithMultipleTargetsTest(TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            MAIN, TestLogger.class, TestLoggerImplementer.class, AnotherImplementer.class)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRule())
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput(true))
        .inspect(inspector -> config.inspect(inspector, true));
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(
            MAIN, TestLogger.class, TestLoggerImplementer.class, AnotherImplementer.class,
            NeverClassInline.class, NeverInline.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRule())
        .noMinification()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput(false))
        .inspect(inspector -> config.inspect(inspector, false));
  }

}
