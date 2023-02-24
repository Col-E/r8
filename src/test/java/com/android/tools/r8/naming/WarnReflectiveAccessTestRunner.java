// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WarnReflectiveAccessTestRunner extends TestBase {

  // See "Method foo" in WarnReflectiveAccessTestMain.main().
  private static int LINE_NUMBER_OF_MARKED_LINE = 28;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  private void reflectionWithBuilder(
      boolean allowDiagnosticWarningMessages,
      boolean explicitRule,
      boolean enableMinification,
      boolean forceProguardCompatibility,
      DiagnosticsConsumer diagnosticsConsumer)
      throws Exception {
    testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
        .addProgramClasses(WarnReflectiveAccessTest.class)
        .addKeepRules(
            "-keepclassmembers class " + WarnReflectiveAccessTest.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .applyIf(
            enableMinification,
            testBuilder ->
                testBuilder.addKeepClassAndMembersRulesWithAllowObfuscation(
                    WarnReflectiveAccessTest.class),
            testBuilder -> testBuilder.addKeepClassAndMembersRules(WarnReflectiveAccessTest.class))
        .addKeepAttributeLineNumberTable()
        .addOptionsModification(InlinerOptions::disableInlining)
        .allowDiagnosticWarningMessages(allowDiagnosticWarningMessages)
        .applyIf(
            explicitRule,
            testBuilder ->
                testBuilder.addKeepRules(
                    "-identifiernamestring class java.lang.Class {",
                    "  static java.lang.Class forName(java.lang.String);",
                    "  java.lang.reflect.Method getDeclaredMethod(java.lang.String,"
                        + " java.lang.Class[]);",
                    "}"))
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(diagnosticsConsumer)
        .run(parameters.getRuntime(), WarnReflectiveAccessTest.class)
        .applyIf(
            enableMinification,
            runResult -> runResult.assertFailureWithErrorThatThrows(NoSuchMethodException.class),
            runResult ->
                runResult.assertSuccessWithOutputThatMatches(containsString("TestMain::foo")));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(WarnReflectiveAccessTest.class)
        .run(parameters.getRuntime(), WarnReflectiveAccessTest.class)
        .assertSuccessWithOutputThatMatches(containsString("TestMain::foo"));
  }

  @Test
  public void test_explicit_minification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(
        true,
        true,
        true,
        true,
        diagnostics -> {
          assertFalse(diagnostics.getWarnings().isEmpty());
          DiagnosticsChecker.checkDiagnostic(
              diagnostics.getWarnings().get(0),
              ToolHelper.getClassFileForTestClass(WarnReflectiveAccessTest.class),
              LINE_NUMBER_OF_MARKED_LINE,
              1,
              "Cannot determine",
              "getDeclaredMethod",
              "-identifiernamestring",
              "resolution failure");
        });
  }

  @Test
  public void test_explicit_noMinification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(
        true,
        true,
        false,
        true,
        diagnostics -> {
          assertFalse(diagnostics.getWarnings().isEmpty());
          DiagnosticsChecker.checkDiagnostic(
              diagnostics.getWarnings().get(0),
              ToolHelper.getClassFileForTestClass(WarnReflectiveAccessTest.class),
              LINE_NUMBER_OF_MARKED_LINE,
              1,
              "Cannot determine",
              "getDeclaredMethod",
              "-identifiernamestring",
              "resolution failure");
        });
  }

  @Test
  public void test_explicit_minification_R8() throws Exception {
    reflectionWithBuilder(
        true,
        true,
        true,
        false,
        diagnostics -> {
          assertFalse(diagnostics.getWarnings().isEmpty());
          DiagnosticsChecker.checkDiagnostic(
              diagnostics.getWarnings().get(0),
              ToolHelper.getClassFileForTestClass(WarnReflectiveAccessTest.class),
              LINE_NUMBER_OF_MARKED_LINE,
              1,
              "Cannot determine",
              "getDeclaredMethod",
              "-identifiernamestring",
              "resolution failure");
        });
  }

  @Test
  public void test_explicit_noMinification_R8() throws Exception {
    reflectionWithBuilder(
        true,
        true,
        false,
        false,
        diagnostics -> {
          assertFalse(diagnostics.getWarnings().isEmpty());
          DiagnosticsChecker.checkDiagnostic(
              diagnostics.getWarnings().get(0),
              ToolHelper.getClassFileForTestClass(WarnReflectiveAccessTest.class),
              LINE_NUMBER_OF_MARKED_LINE,
              1,
              "Cannot determine",
              "getDeclaredMethod",
              "-identifiernamestring",
              "resolution failure");
        });
  }

  @Test
  public void test_implicit_minification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(false, false, true, true, TestDiagnosticMessages::assertNoWarnings);
  }

  @Test
  public void test_implicit_noMinification_forceProguardCompatibility() throws Exception {
    reflectionWithBuilder(false, false, false, true, TestDiagnosticMessages::assertNoWarnings);
  }

  @Test
  public void test_implicit_minification_R8() throws Exception {
    reflectionWithBuilder(false, false, true, false, TestDiagnosticMessages::assertNoWarnings);
  }

  @Test
  public void test_implicit_noMinification_R8() throws Exception {
    reflectionWithBuilder(false, false, false, false, TestDiagnosticMessages::assertNoWarnings);
  }
}
