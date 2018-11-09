// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;
import org.hamcrest.Matcher;

public class TestRunResult {
  protected final AndroidApp app;
  private final ProcessResult result;

  public TestRunResult(AndroidApp app, ProcessResult result) {
    this.app = app;
    this.result = result;
  }

  public TestRunResult assertSuccess() {
    assertEquals(errorMessage("Expected run to succeed."), 0, result.exitCode);
    return this;
  }

  public TestRunResult assertFailure() {
    assertNotEquals(errorMessage("Expected run to fail."), 0, result.exitCode);
    return this;
  }

  public TestRunResult assertFailureWithOutput(String expected) {
    assertFailure();
    assertEquals(errorMessage("Run stdout incorrect.", expected), expected, result.stdout);
    return this;
  }

  public TestRunResult assertFailureWithErrorThatMatches(Matcher<String> matcher) {
    assertFailure();
    assertThat(
        errorMessage("Run stderr incorrect.", matcher.toString()), result.stderr, matcher);
    return this;
  }

  public TestRunResult assertSuccessWithOutput(String expected) {
    assertSuccess();
    assertEquals(errorMessage("Run stdout incorrect.", expected), expected, result.stdout);
    return this;
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    // Inspection post run implies success. If inspection of an invalid program is needed it should
    // be done on the compilation result or on the input.
    assertSuccess();
    assertNotNull(app);
    return new CodeInspector(app);
  }

  public TestRunResult inspect(Consumer<CodeInspector> consumer)
      throws IOException, ExecutionException {
    consumer.accept(inspector());
    return this;
  }

  private String errorMessage(String message) {
    return errorMessage(message, null);
  }

  private String errorMessage(String message, String expected) {
    StringBuilder builder = new StringBuilder(message).append('\n');
    if (expected != null) {
      if (expected.contains(System.lineSeparator())) {
        builder.append("EXPECTED:").append(System.lineSeparator()).append(expected);
      } else {
        builder.append("EXPECTED: ").append(expected);
      }
      builder.append(System.lineSeparator());
    }
    appendInfo(builder);
    return builder.toString();
  }

  private void appendInfo(StringBuilder builder) {
    builder.append("APPLICATION: ");
    appendApplication(builder);
    builder.append('\n');
    appendProcessResult(builder);
  }

  private void appendApplication(StringBuilder builder) {
    builder.append(app == null ? "<default>" : app.toString());
  }

  private void appendProcessResult(StringBuilder builder) {
    builder.append("COMMAND: ").append(result.command).append('\n').append(result);
  }

  public TestRunResult writeInfo(PrintStream ps) {
    StringBuilder sb = new StringBuilder();
    appendInfo(sb);
    ps.println(sb.toString());
    return this;
  }

  public TestRunResult writeApplicaion(PrintStream ps) {
    StringBuilder sb = new StringBuilder();
    appendApplication(sb);
    ps.println(sb.toString());
    return this;
  }

  public TestRunResult writeProcessResult(PrintStream ps) {
    StringBuilder sb = new StringBuilder();
    appendProcessResult(sb);
    ps.println(sb.toString());
    return this;
  }
}
