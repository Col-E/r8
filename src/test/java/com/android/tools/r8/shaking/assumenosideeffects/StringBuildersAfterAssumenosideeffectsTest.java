// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;

class TestClassAfterAssumenosideeffects {
  public static void main(String... args) {
    TestLogger instance = new TestLoggerImplementer();
    StringBuilder builder = new StringBuilder();
    builder.append("Hello");
    builder.append(args.length == 0 ? ", R8" : args[0]);
    instance.info("TAG", builder.toString());
    System.out.println("The end");
  }
}

@RunWith(Parameterized.class)
public class StringBuildersAfterAssumenosideeffectsTest extends TestBase {
  private static final Class<?> MAIN = TestClassAfterAssumenosideeffects.class;
  private static final String EXPECTED = StringUtils.lines("The end");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public StringBuildersAfterAssumenosideeffectsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN, TestLogger.class, TestLoggerImplementer.class)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(
            "-assumenosideeffects class * implements **.TestLogger {",
            "  void info(...);",
            "}")
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(
        0,
        Streams.stream(mainMethod.iterateInstructions(
            i -> i.isInvoke() && i.getMethod().name.toString().equals("info"))).count());

    // TODO(b/137038659): StringBuilders (and all other calls) can be gone.
    assertNotEquals(
        0,
        Streams.stream(mainMethod.iterateInstructions(
            i -> i.isInvoke()
                && i.getMethod().holder.toDescriptorString().contains("StringBuilder"))));
  }
}
