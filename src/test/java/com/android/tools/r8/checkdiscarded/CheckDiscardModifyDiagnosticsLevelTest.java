// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.checkdiscarded.testclasses.Main;
import com.android.tools.r8.checkdiscarded.testclasses.UnusedClass;
import com.android.tools.r8.checkdiscarded.testclasses.UsedClass;
import com.android.tools.r8.checkdiscarded.testclasses.WillBeGone;
import com.android.tools.r8.checkdiscarded.testclasses.WillStay;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckDiscardModifyDiagnosticsLevelTest extends TestBase {

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(), DiagnosticsLevel.values());
  }

  private final DiagnosticsLevel mappedLevel;

  public CheckDiscardModifyDiagnosticsLevelTest(TestParameters parameters, DiagnosticsLevel level) {
    parameters.assertNoneRuntime();
    this.mappedLevel = level;
  }

  private void noInlining(InternalOptions options) {
    options.inlinerOptions().enableInlining = false;
  }

  private Matcher<Diagnostic> discardCheckFailedMatcher() {
    return diagnosticMessage(
        allOf(
            startsWith("Discard checks failed"),
            containsString("UsedClass was not discarded"),
            containsString("is instantiated in")));
  }

  private Collection<Matcher<Diagnostic>> errorMatchers() {
    return mappedLevel == DiagnosticsLevel.ERROR
        ? ImmutableList.of(discardCheckFailedMatcher())
        : ImmutableList.of();
  }

  private Collection<Matcher<Diagnostic>> warningMatchers() {
    return mappedLevel == DiagnosticsLevel.WARNING
        ? ImmutableList.of(discardCheckFailedMatcher())
        : ImmutableList.of();
  }

  private Collection<Matcher<Diagnostic>> infoMatchers() {
    return mappedLevel == DiagnosticsLevel.INFO
        ? ImmutableList.of(discardCheckFailedMatcher())
        : ImmutableList.of();
  }

  @Test
  public void dontFailCompilationIfCheckDiscardedFails() {
    try {
      testForR8(Backend.DEX)
          .addProgramClasses(
              UnusedClass.class, UsedClass.class, Main.class, WillBeGone.class, WillStay.class)
          .addKeepMainRule(Main.class)
          .addKeepRules("-checkdiscard class " + UsedClass.class.getTypeName())
          .addOptionsModification(this::noInlining)
          .setDiagnosticsLevelModifier(
              (level, diagnostic) -> {
                if (diagnostic instanceof CheckDiscardDiagnostic) {
                  return mappedLevel;
                } else {
                  return level;
                }
              })
          .allowDiagnosticMessages()
          .compileWithExpectedDiagnostics(
              diagnostics ->
                  diagnostics
                      .assertErrorsMatch(errorMatchers())
                      .assertWarningsMatch(warningMatchers())
                      .assertInfosMatch(infoMatchers()));
      assertTrue(
          mappedLevel == DiagnosticsLevel.INFO
              || mappedLevel == DiagnosticsLevel.WARNING
              || mappedLevel == DiagnosticsLevel.NONE);
    } catch (CompilationFailedException e) {
      assertEquals(mappedLevel, DiagnosticsLevel.ERROR);
    }
  }
}
