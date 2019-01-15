// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matcher;

public class TestDiagnosticMessagesImpl implements DiagnosticsHandler, TestDiagnosticMessages {
  private final List<Diagnostic> infos = new ArrayList<>();
  private final List<Diagnostic> warnings = new ArrayList<>();
  private final List<Diagnostic> errors = new ArrayList<>();

  @Override
  public void info(Diagnostic info) {
    infos.add(info);
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


  public TestDiagnosticMessages assertNoMessages() {
    assertEquals(0, getInfos().size());
    assertEquals(0, getWarnings().size());
    assertEquals(0, getErrors().size());
    return this;
  }

  public TestDiagnosticMessages assertOnlyInfos() {
    assertNotEquals(0, getInfos().size());
    assertEquals(0, getWarnings().size());
    assertEquals(0, getErrors().size());
    return this;
  }

  public TestDiagnosticMessages assertOnlyWarnings() {
    assertEquals(0, getInfos().size());
    assertNotEquals(0, getWarnings().size());
    assertEquals(0, getErrors().size());
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

  public TestDiagnosticMessages assertWarningMessageThatMatches(Matcher<String> matcher) {
    assertNotEquals(0, getWarnings().size());
    for (int i = 0; i < getWarnings().size(); i++) {
      if (matcher.matches(getWarnings().get(i).getDiagnosticMessage())) {
        return this;
      }
    }
    StringBuilder builder = new StringBuilder("No warning matches " + matcher.toString());
    builder.append(System.lineSeparator());
    if (getWarnings().size() == 0) {
      builder.append("There where no warnings.");
    } else {
      builder.append("There where " + getWarnings().size() + " warnings:");
      builder.append(System.lineSeparator());
      for (int i = 0; i < getWarnings().size(); i++) {
        builder.append(getWarnings().get(i).getDiagnosticMessage());
        builder.append(System.lineSeparator());
      }
    }
    fail(builder.toString());
    return this;
  }

  public TestDiagnosticMessages assertNoWarningMessageThatMatches(Matcher<String> matcher) {
    assertNotEquals(0, getWarnings().size());
    for (int i = 0; i < getWarnings().size(); i++) {
      String message = getWarnings().get(i).getDiagnosticMessage();
      if (matcher.matches(message)) {
        fail("The warning: \"" + message + "\" + matches " + matcher + ".");
      }
    }
    return this;
  }
}
