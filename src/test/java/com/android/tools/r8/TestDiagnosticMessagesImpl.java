// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matcher;

public class TestDiagnosticMessagesImpl implements DiagnosticsHandler, TestDiagnosticMessages {
  private final List<Diagnostic> infos = new ArrayList<>();
  private final List<Diagnostic> warnings = new ArrayList<>();
  private final List<Diagnostic> errors = new ArrayList<>();

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
    warnings.add(warning);
  }

  @Override
  public void error(Diagnostic error) {
    errors.add(error);
  }

  public List<Diagnostic> getInfos() {
    return infos;
  }

  public List<Diagnostic> getWarnings() {
    return warnings;
  }

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

  public TestDiagnosticMessages assertNoMessages() {
    assertEmpty("info", getInfos());
    assertEmpty("warning", getWarnings());
    assertEmpty("error", getErrors());
    return this;
  }

  public TestDiagnosticMessages assertOnlyInfos() {
    assertNotEquals(0, getInfos().size());
    assertEmpty("warning", getWarnings());
    assertEmpty("error", getErrors());
    return this;
  }

  public TestDiagnosticMessages assertOnlyWarnings() {
    assertEmpty("info", getInfos());
    assertNotEquals(0, getWarnings().size());
    assertEmpty("error", getErrors());
    return this;
  }

  public TestDiagnosticMessages assertOnlyErrors() {
    assertEmpty("info", getInfos());
    assertEmpty("warning", getWarnings());
    assertNotEquals(0, getErrors().size());
    return this;
  }

  public TestDiagnosticMessages assertInfosCount(int count) {
    assertEquals(count, getInfos().size());
    return this;
  }

  public TestDiagnosticMessages assertWarningsCount(int count) {
    assertEquals(count, getWarnings().size());
    return this;
  }

  public TestDiagnosticMessages assertErrorsCount(int count) {
    assertEquals(count, getErrors().size());
    return this;
  }

  private TestDiagnosticMessages assertMessageThatMatches(
      List<Diagnostic> diagnostics, String tag, Matcher<String> matcher) {
    assertNotEquals(0, diagnostics.size());
    for (int i = 0; i < diagnostics.size(); i++) {
      if (matcher.matches(diagnostics.get(i).getDiagnosticMessage())) {
        return this;
      }
    }
    StringBuilder builder = new StringBuilder("No " + tag + " matches " + matcher.toString());
    builder.append(System.lineSeparator());
    if (getWarnings().size() == 0) {
      builder.append("There were no " + tag + "s.");
    } else {
      builder.append("There were " + diagnostics.size() + " "+ tag + "s:");
      builder.append(System.lineSeparator());
      for (int i = 0; i < diagnostics.size(); i++) {
        builder.append(diagnostics.get(i).getDiagnosticMessage());
        builder.append(System.lineSeparator());
      }
    }
    fail(builder.toString());
    return this;
  }

  private TestDiagnosticMessages assertNoMessageThatMatches(
      List<Diagnostic> diagnostics, String tag, Matcher<String> matcher) {
    for (int i = 0; i < diagnostics.size(); i++) {
      String message = diagnostics.get(i).getDiagnosticMessage();
      if (matcher.matches(message)) {
        fail("The " + tag + ": \"" + message + "\" + matches " + matcher + ".");
      }
    }
    return this;
  }

  public TestDiagnosticMessages assertInfoMessageThatMatches(Matcher<String> matcher) {
    return assertMessageThatMatches(getInfos(), "info", matcher);
  }

  public TestDiagnosticMessages assertNoInfoMessageThatMatches(Matcher<String> matcher) {
    return assertNoMessageThatMatches(getInfos(), "info", matcher);
  }

  public TestDiagnosticMessages assertWarningMessageThatMatches(Matcher<String> matcher) {
    return assertMessageThatMatches(getWarnings(), "warning", matcher);
  }

  public TestDiagnosticMessages assertNoWarningMessageThatMatches(Matcher<String> matcher) {
    return assertNoMessageThatMatches(getWarnings(), "warning", matcher);
  }

  @Override
  public TestDiagnosticMessages assertErrorMessageThatMatches(Matcher<String> matcher) {
    return assertMessageThatMatches(getErrors(), "error", matcher);
  }

  @Override
  public TestDiagnosticMessages assertNoErrorMessageThatMatches(Matcher<String> matcher) {
    return assertNoMessageThatMatches(getErrors(), "error", matcher);
  }
}
