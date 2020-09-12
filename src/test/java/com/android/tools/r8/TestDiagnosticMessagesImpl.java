// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.hamcrest.Matcher;

public class TestDiagnosticMessagesImpl implements DiagnosticsHandler, TestDiagnosticMessages {
  private final List<Diagnostic> infos = new ArrayList<>();
  private final List<Diagnostic> warnings = new ArrayList<>();
  private final List<Diagnostic> errors = new ArrayList<>();
  BiFunction<DiagnosticsLevel, Diagnostic, DiagnosticsLevel> modifier;

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Infos: ").append('\n');
    for (Diagnostic info : infos) {
      builder.append("  - ").append(info.getDiagnosticMessage()).append('\n');
    }
    builder.append("Warnings: ").append('\n');
    for (Diagnostic warning : warnings) {
      builder.append("  - ").append(warning.getDiagnosticMessage()).append('\n');
    }
    builder.append("Errors: ").append('\n');
    for (Diagnostic error : errors) {
      builder.append("  - ").append(error.getDiagnosticMessage()).append('\n');
    }
    return builder.toString();
  }

  @Override
  public void info(Diagnostic info) {
    // We are almost always compiling with assertions enabled and R8 will print a message. We
    // discard the message here because for almost all tests, this message is not relevant.
    if (!info.getDiagnosticMessage()
        .equals("Running R8 version " + Version.LABEL + " with assertions enabled.")) {
      infos.add(info);
    }
  }

  @Override
  public void warning(Diagnostic warning) {
    // When testing D8 with class file output this warning is always emitted. Discard this, as
    // for tests this is not relevant.
    if (!warning.equals("Compiling to Java class files with D8 is not officially supported")) {
      warnings.add(warning);
    }
  }

  @Override
  public void error(Diagnostic error) {
    errors.add(error);
  }

  @Override
  public DiagnosticsLevel modifyDiagnosticsLevel(DiagnosticsLevel level, Diagnostic diagnostic) {
    return modifier == null ? level : modifier.apply(level, diagnostic);
  }

  @Override
  public List<Diagnostic> getInfos() {
    return infos;
  }

  @Override
  public List<Diagnostic> getWarnings() {
    return warnings;
  }

  @Override
  public List<Diagnostic> getErrors() {
    return errors;
  }

  private void assertEmpty(String type, List<Diagnostic> messages) {
    assertEquals(
        "Expected no "
            + type
            + " messages, got:\n"
            + String.join("\n", ListUtils.map(messages, m -> m.getDiagnosticMessage())),
        0,
        messages.size());
  }

  @Override
  public TestDiagnosticMessages assertNoMessages() {
    assertEmpty("info", getInfos());
    assertEmpty("warning", getWarnings());
    assertEmpty("error", getErrors());
    return this;
  }

  @Override
  public TestDiagnosticMessages assertOnlyInfos() {
    assertNotEquals(0, getInfos().size());
    assertEmpty("warning", getWarnings());
    assertEmpty("error", getErrors());
    return this;
  }

  @Override
  public TestDiagnosticMessages assertOnlyWarnings() {
    assertEmpty("info", getInfos());
    assertNotEquals(0, getWarnings().size());
    assertEmpty("error", getErrors());
    return this;
  }

  @Override
  public TestDiagnosticMessages assertOnlyErrors() {
    assertEmpty("info", getInfos());
    assertEmpty("warning", getWarnings());
    assertNotEquals(0, getErrors().size());
    return this;
  }

  @Override
  public TestDiagnosticMessages assertInfosCount(int count) {
    assertEquals(count, getInfos().size());
    return this;
  }

  @Override
  public TestDiagnosticMessages assertWarningsCount(int count) {
    assertEquals(count, getWarnings().size());
    return this;
  }

  @Override
  public TestDiagnosticMessages assertErrorsCount(int count) {
    assertEquals(count, getErrors().size());
    return this;
  }

  private TestDiagnosticMessages assertAllDiagnosticsMatches(
      Iterable<Diagnostic> diagnostics, String tag, Matcher<Diagnostic> matcher) {
    for (Diagnostic diagnostic : diagnostics) {
      assertThat(diagnostic, matcher);
    }
    return this;
  }

  private TestDiagnosticMessages assertDiagnosticThatMatches(
      Iterable<Diagnostic> diagnostics, String tag, Matcher<Diagnostic> matcher) {
    int numberOfDiagnostics = 0;
    for (Diagnostic diagnostic : diagnostics) {
      if (matcher.matches(diagnostic)) {
        return this;
      }
      numberOfDiagnostics++;
    }
    StringBuilder builder = new StringBuilder("No " + tag + " matches " + matcher.toString());
    builder.append(System.lineSeparator());
    if (numberOfDiagnostics == 0) {
      builder.append("There were no " + tag + "s.");
    } else {
      builder.append("There were " + numberOfDiagnostics + " " + tag + "s:");
      builder.append(System.lineSeparator());
      for (Diagnostic diagnostic : diagnostics) {
        builder.append(diagnostic.getDiagnosticMessage());
        builder.append(System.lineSeparator());
      }
    }
    fail(builder.toString());
    return this;
  }

  private static void assertDiagnosticsMatch(
      Iterable<Diagnostic> diagnostics, String tag, Collection<Matcher<Diagnostic>> matchers) {
    // Match is unordered, but we make no attempts to find the maximum match.
    int diagnosticsCount = 0;
    Set<Diagnostic> matchedDiagnostics = new HashSet<>();
    Set<Matcher<Diagnostic>> matchedMatchers = new HashSet<>();
    for (Diagnostic diagnostic : diagnostics) {
      diagnosticsCount++;
      for (Matcher<Diagnostic> matcher : matchers) {
        if (matchedMatchers.contains(matcher)) {
          continue;
        }
        if (matcher.matches(diagnostic)) {
          matchedDiagnostics.add(diagnostic);
          matchedMatchers.add(matcher);
          break;
        }
      }
    }
    StringBuilder builder = new StringBuilder();
    boolean failedMatching = false;
    if (matchedDiagnostics.size() < diagnosticsCount) {
      failedMatching = true;
      builder.append("\nUnmatched diagnostics:");
      for (Diagnostic diagnostic : diagnostics) {
        if (!matchedDiagnostics.contains(diagnostic)) {
          builder
              .append("\n  - ")
              .append(diagnostic.getClass().getName())
              .append(": ")
              .append(diagnostic.getDiagnosticMessage());
        }
      }
    }
    if (matchedMatchers.size() < matchers.size()) {
      failedMatching = true;
      builder.append("\nUnmatched matchers:");
      for (Matcher<Diagnostic> matcher : matchers) {
        if (!matchedMatchers.contains(matcher)) {
          builder.append("\n  - ").append(matcher);
        }
      }
    }
    if (failedMatching) {
      builder.append("\nAll diagnostics:");
      for (Diagnostic diagnostic : diagnostics) {
        builder
            .append("\n  - ")
            .append(diagnostic.getClass().getName())
            .append(": ")
            .append(diagnostic.getDiagnosticMessage());
      }
      builder.append("\nAll matchers:");
      for (Matcher<Diagnostic> matcher : matchers) {
        builder.append("\n  - ").append(matcher);
      }
      fail(builder.toString());
    }
    // Double check consistency.
    assertEquals(matchers.size(), diagnosticsCount);
    assertEquals(diagnosticsCount, matchedDiagnostics.size());
    assertEquals(diagnosticsCount, matchedMatchers.size());
  }

  @Override
  public TestDiagnosticMessages assertDiagnosticsMatch(Collection<Matcher<Diagnostic>> matchers) {
    assertDiagnosticsMatch(getAllDiagnostics(), "diagnostics", matchers);
    return this;
  }

  @Override
  public TestDiagnosticMessages assertInfosMatch(Collection<Matcher<Diagnostic>> matchers) {
    assertDiagnosticsMatch(getInfos(), "infos", matchers);
    return this;
  }

  @Override
  public TestDiagnosticMessages assertWarningsMatch(Collection<Matcher<Diagnostic>> matchers) {
    assertDiagnosticsMatch(getWarnings(), "warnings", matchers);
    return this;
  }

  @Override
  public TestDiagnosticMessages assertErrorsMatch(Collection<Matcher<Diagnostic>> matchers) {
    assertDiagnosticsMatch(getErrors(), "errors", matchers);
    return this;
  }

  @Override
  public TestDiagnosticMessages assertDiagnosticThatMatches(Matcher<Diagnostic> matcher) {
    return assertDiagnosticThatMatches(getAllDiagnostics(), "diagnostic message", matcher);
  }

  private Iterable<Diagnostic> getAllDiagnostics() {
    return Iterables.concat(getInfos(), getWarnings(), getErrors());
  }

  @Override
  public TestDiagnosticMessages assertInfoThatMatches(Matcher<Diagnostic> matcher) {
    return assertDiagnosticThatMatches(getInfos(), "info", matcher);
  }

  @Override
  public TestDiagnosticMessages assertWarningThatMatches(Matcher<Diagnostic> matcher) {
    return assertDiagnosticThatMatches(getWarnings(), "warning", matcher);
  }

  @Override
  public TestDiagnosticMessages assertErrorThatMatches(Matcher<Diagnostic> matcher) {
    return assertDiagnosticThatMatches(getErrors(), "error", matcher);
  }

  @Override
  public TestDiagnosticMessages assertAllDiagnosticsMatch(Matcher<Diagnostic> matcher) {
    return assertAllDiagnosticsMatches(getAllDiagnostics(), "diagnostic message", matcher);
  }

  @Override
  public TestDiagnosticMessages assertAllInfosMatch(Matcher<Diagnostic> matcher) {
    return assertAllDiagnosticsMatches(getInfos(), "info", matcher);
  }

  @Override
  public TestDiagnosticMessages assertAllWarningsMatch(Matcher<Diagnostic> matcher) {
    return assertAllDiagnosticsMatches(getWarnings(), "warning", matcher);
  }

  @Override
  public TestDiagnosticMessages assertAllErrorsMatch(Matcher<Diagnostic> matcher) {
    return assertAllDiagnosticsMatches(getErrors(), "error", matcher);
  }

  void setDiagnosticsLevelModifier(
      BiFunction<DiagnosticsLevel, Diagnostic, DiagnosticsLevel> modifier) {
    this.modifier = modifier;
  }
}
