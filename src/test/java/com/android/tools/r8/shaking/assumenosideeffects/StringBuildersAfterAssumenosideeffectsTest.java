// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuildersAfterAssumenosideeffectsTest extends TestBase {
  private static final Class<?> MAIN = TestClassAfterAssumenosideeffects.class;
  private static final String EXPECTED = StringUtils.lines("The end");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StringBuildersAfterAssumenosideeffectsTest.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(
            "-assumenosideeffects class * implements " + TestLogger.class.getTypeName() + " {",
            "  void info(...);",
            "}")
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());
    assertTrue(
        mainMethod.streamInstructions().noneMatch(
            i -> i.isInvoke() && i.getMethod().name.toString().equals("info")));

    assertTrue(
        mainMethod.streamInstructions().noneMatch(
            i -> i.isInvoke()
                && i.getMethod().holder.toDescriptorString().contains("StringBuilder")));
  }

  interface TestLogger {
    void info(String tag, String message);
  }

  @NeverClassInline
  static class TestLoggerImplementer implements TestLogger {

    @NeverInline
    @Override
    public void info(String tag, String message) {
      System.out.println(tag + ": " + message);
    }
  }

  static class TestClassAfterAssumenosideeffects {
    public static void main(String... args) {
      TestLogger instance = new TestLoggerImplementer();
      StringBuilder builder = new StringBuilder();
      builder.append("Hello");
      builder.append(args.length == 0 ? ", R8" : args[0]);
      instance.info("TAG", builder.toString());
      System.out.println("The end");
    }
  }
}
