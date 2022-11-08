// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.diagnostics;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionsDiagnosticImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ModifyDiagnosticsLevelTest extends TestBase {

  private static final String MISSING_CLASS_MESSAGE_PREFIX = "Missing class ";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ModifyDiagnosticsLevelTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testWarningToInfo() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-ignorewarnings")
        .setDiagnosticsLevelModifier(
            (level, diagnostic) -> {
              if (level == DiagnosticsLevel.WARNING
                  && diagnostic instanceof MissingDefinitionsDiagnosticImpl
                  && diagnostic.getDiagnosticMessage().startsWith(MISSING_CLASS_MESSAGE_PREFIX)) {
                return DiagnosticsLevel.INFO;
              }
              return level;
            })
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyInfos()
                    .assertInfosCount(1)
                    .assertInfosMatch(diagnosticMessage(startsWith(MISSING_CLASS_MESSAGE_PREFIX))));
  }

  @Test
  public void testWarningToError() {
    try {
      testForR8(Backend.DEX)
          .addProgramClasses(TestClass.class)
          .addKeepMainRule(TestClass.class)
          .addKeepRules("-ignorewarnings")
          .setDiagnosticsLevelModifier(
              (level, diagnostic) -> {
                if (level == DiagnosticsLevel.WARNING
                    && diagnostic instanceof MissingDefinitionsDiagnosticImpl
                    && diagnostic.getDiagnosticMessage().startsWith(MISSING_CLASS_MESSAGE_PREFIX)) {
                  return DiagnosticsLevel.ERROR;
                }
                return level;
              })
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics
                    .assertOnlyErrors()
                    .assertErrorsCount(1)
                    .assertErrorsMatch(diagnosticMessage(startsWith(MISSING_CLASS_MESSAGE_PREFIX)));
              });
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testWarningToNone() throws CompilationFailedException {
    testForR8(Backend.DEX)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-ignorewarnings")
        .setDiagnosticsLevelModifier(
            (level, diagnostic) -> {
              if (level == DiagnosticsLevel.WARNING
                  && diagnostic instanceof MissingDefinitionsDiagnosticImpl
                  && diagnostic.getDiagnosticMessage().startsWith(MISSING_CLASS_MESSAGE_PREFIX)) {
                return DiagnosticsLevel.NONE;
              }
              return level;
            })
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  static class TestClass implements I {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  interface I {}
}
