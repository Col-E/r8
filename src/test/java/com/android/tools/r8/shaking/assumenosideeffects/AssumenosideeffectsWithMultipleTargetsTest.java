// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
              "-assumenosideeffects class " + TestLogger.class.getTypeName() + " {",
              "  void info(...);",
              "}");
        case RULE_THAT_DIRECTLY_REFERS_INTERFACE:
          return StringUtils.lines(
              "-assumenosideeffects interface " + TestLogger.class.getTypeName() + " {",
              "  void info(...);",
              "}");
        case RULE_WITH_IMPLEMENTS:
          return StringUtils.lines(
              "-assumenosideeffects class * implements " + TestLogger.class.getTypeName() + " {",
              "  void info(...);",
              "}");
      }
      throw new Unreachable();
    }

    static final String OUTPUT_WITHOUT_INFO = StringUtils.lines(
        "The end"
    );

    public void inspect(CodeInspector inspector) {
      ClassSubject main = inspector.clazz(MAIN);
      assertThat(main, isPresent());

      MethodSubject mainMethod = main.mainMethod();
      assertThat(mainMethod, isPresent());
      assertTrue(
          mainMethod.streamInstructions().noneMatch(
              i -> i.isInvoke() && i.getMethod().name.toString().equals("info")));

      MethodSubject testInvokeInterface = main.uniqueMethodWithOriginalName("testInvokeInterface");
      assertThat(testInvokeInterface, not(isPresent()));

      FieldSubject tag = main.uniqueFieldWithOriginalName("TAG");
      assertThat(tag, not(isPresent()));
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
        .addInnerClasses(AssumenosideeffectsWithMultipleTargetsTest.class)
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRule())
        .addDontObfuscate()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(TestConfig.OUTPUT_WITHOUT_INFO)
        .inspect(config::inspect);
  }

  interface TestLogger {
    void info(String tag, String message);
  }

  @NoVerticalClassMerging
  abstract static class AbstractLogger implements TestLogger {}

  @NeverClassInline
  static class TestLoggerImplementer implements TestLogger {

    @NeverInline
    @Override
    public void info(String tag, String message) {
      System.out.println(tag + ": " + message);
    }
  }

  @NeverClassInline
  static class AnotherImplementer extends AbstractLogger implements TestLogger {

    @NeverInline
    @Override
    public void info(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
    }
  }

  static class TestClass {
    final static String TAG = TestClass.class.getSimpleName();

    @NeverInline
    private static void testInvokeInterface(TestLogger logger, String message) {
      if (logger != null) {
        logger.info(TAG, message);
      }
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
}
