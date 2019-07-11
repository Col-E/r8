// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

interface LoggerInterface {
  void debug(String message);
  void debug(String tag, String message);
}

@NeverMerge
class BaseImplementer implements LoggerInterface {
  @Override
  public void debug(String message) {
    System.out.println("[Base]: " + message);
  }

  @Override
  public void debug(String tag, String message) {
    System.out.println(tag + ": " + message);
  }
}

class SubImplementer extends BaseImplementer implements LoggerInterface {
  // Intentionally empty
  // b/137038659: Since there is no method definition in this class, no rules are bound.
  // We propagated assume* rules only if all the subtypes' corresponding methods have the same rule.
  // The lack of matching definitions in this sub type blocks us from marking methods in the super
  // type, in this example, LoggerInterface#debug(...).
}

// To bother single target resolution. In fact, not used at all.
class AnotherLogger implements LoggerInterface {
  @Override
  public void debug(String message) {
    System.out.println("[AnotherLogger]: " + message);
  }

  @Override
  public void debug(String tag, String message) {
    System.out.println("[" + tag + "] " + message);
  }
}

class B137038659 {
  final static String TAG = B137038659.class.getSimpleName();

  @NeverInline
  private static void testInvokeInterface(LoggerInterface logger, String message) {
    logger.debug(TAG, message);
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

@RunWith(Parameterized.class)
public class AssumenosideeffectsPropagationWithoutMatchingDefinitionTest extends TestBase {
  private static final Class<?> MAIN = B137038659.class;

  private static final String JVM_OUTPUT = StringUtils.lines(
      "B137038659: message1",
      "[Base]: message2",
      "The end"
  );
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
        .addProgramClasses(
            MAIN,
            LoggerInterface.class,
            BaseImplementer.class,
            SubImplementer.class,
            AnotherLogger.class)
        .enableMergeAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(
            "-assumenosideeffects class * implements **.LoggerInterface {",
            "  *** debug(...);",
            "}")
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        // TODO(b/137038659): should be able to remove logging.
        .assertSuccessWithOutput(JVM_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());
    // TODO(b/137038659): can be zero.
    assertNotEquals(
        0,
        Streams.stream(mainMethod.iterateInstructions(
            i -> i.isInvoke() && i.getMethod().name.toString().equals("debug"))).count());

    MethodSubject testInvokeInterface = main.uniqueMethodWithName("testInvokeInterface");
    // TODO(b/137038659): can be removed entirely.
    assertThat(testInvokeInterface, isPresent());
  }
}
