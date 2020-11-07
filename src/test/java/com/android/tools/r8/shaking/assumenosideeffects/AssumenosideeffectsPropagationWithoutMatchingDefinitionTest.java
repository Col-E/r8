// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumenosideeffectsPropagationWithoutMatchingDefinitionTest extends TestBase {
  private static final Class<?> MAIN = B137038659.class;
  private static final String OUTPUT_WITHOUT_LOGGING = StringUtils.lines(
      "The end"
  );

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public AssumenosideeffectsPropagationWithoutMatchingDefinitionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumenosideeffectsPropagationWithoutMatchingDefinitionTest.class)
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(
            "-assumenosideeffects class * implements " + LoggerInterface.class.getTypeName() + " {",
            "  *** debug(...);",
            "}")
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(OUTPUT_WITHOUT_LOGGING)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());
    assertTrue(
        mainMethod.streamInstructions().noneMatch(
            i -> i.isInvoke() && i.getMethod().name.toString().equals("debug")));

    MethodSubject testInvokeInterface = main.uniqueMethodWithName("testInvokeInterface");
    assertThat(testInvokeInterface, not(isPresent()));
  }

  interface LoggerInterface {
    void debug(String message);
    void debug(String tag, String message);
  }

  @NoVerticalClassMerging
  static class BaseImplementer implements LoggerInterface {
    @Override
    public void debug(String message) {
      System.out.println("[Base]: " + message);
    }

    @Override
    public void debug(String tag, String message) {
      System.out.println(tag + ": " + message);
    }
  }

  static class SubImplementer extends BaseImplementer implements LoggerInterface {
    // Intentionally empty
    // b/137038659: Since there is no method definition in this class, no rules are bound. We
    // propagated assume* rules only if all the subtypes' corresponding methods have the same rule.
    // The lack of matching definitions in this sub type blocks us from marking methods in the super
    // type, in this example, LoggerInterface#debug(...).
    // By using a resolved target, we will look up a rule for BaseImplementer#debug(...) instead.
  }

  // To bother single target resolution. In fact, not used at all.
  @NoHorizontalClassMerging
  static class AnotherLogger implements LoggerInterface {
    @Override
    public void debug(String message) {
      System.out.println("[AnotherLogger]: " + message);
    }

    @Override
    public void debug(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
    }
  }

  static class B137038659 {
    final static String TAG = B137038659.class.getSimpleName();

    @NeverInline
    private static void testInvokeInterface(LoggerInterface logger, String message) {
      if (logger != null) {
        logger.debug(TAG, message);
      }
    }

    @NeverInline
    private static LoggerInterface getLogger() {
      return System.currentTimeMillis() > 0 ? new SubImplementer()
          : (System.nanoTime() > 0 ? new BaseImplementer() : new AnotherLogger());
    }

    public static void main(String... args) {
      LoggerInterface logger = getLogger();
      testInvokeInterface(logger, "message1");
      logger.debug("message2");
      System.out.println("The end");
    }
  }
}
