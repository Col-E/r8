// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.hamcrest.Matcher;

public abstract class TestRunResult<RR extends TestRunResult<?>> {
  protected final AndroidApp app;
  private final ProcessResult result;

  public TestRunResult(AndroidApp app, ProcessResult result) {
    this.app = app;
    this.result = result;
  }

  abstract RR self();

  public <S> S map(Function<RR, S> fn) {
    return fn.apply(self());
  }

  public RR apply(Consumer<RR> fn) {
    fn.accept(self());
    return self();
  }

  public AndroidApp app() {
    return app;
  }

  public String getStdOut() {
    return result.stdout;
  }

  public String getStdErr() {
    return result.stderr;
  }

  public int getExitCode() {
    return result.exitCode;
  }

  public RR assertSuccess() {
    assertEquals(errorMessage("Expected run to succeed."), 0, result.exitCode);
    return self();
  }

  public RR assertFailure() {
    assertNotEquals(errorMessage("Expected run to fail."), 0, result.exitCode);
    return self();
  }

  public RR assertFailureWithOutput(String expected) {
    assertFailure();
    assertEquals(errorMessage("Run stdout incorrect.", expected), expected, result.stdout);
    return self();
  }

  public RR assertFailureWithErrorThatMatches(Matcher<String> matcher) {
    assertFailure();
    assertThat(
        errorMessage("Run stderr incorrect.", matcher.toString()), result.stderr, matcher);
    return self();
  }

  public RR assertSuccessWithOutput(String expected) {
    assertSuccess();
    assertEquals(errorMessage("Run stdout incorrect.", expected), expected, result.stdout);
    return self();
  }

  public RR assertSuccessWithOutputLines(String... expected) {
    return assertSuccessWithOutput(StringUtils.lines(expected));
  }

  public RR assertSuccessWithOutputThatMatches(Matcher<String> matcher) {
    assertSuccess();
    assertThat(errorMessage("Run stdout incorrect.", matcher.toString()), result.stdout, matcher);
    return self();
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    // Inspection post run implies success. If inspection of an invalid program is needed it should
    // be done on the compilation result or on the input.
    assertSuccess();
    assertNotNull(app);
    return new CodeInspector(app);
  }

  public RR inspect(Consumer<CodeInspector> consumer)
      throws IOException, ExecutionException {
    consumer.accept(inspector());
    return self();
  }

  public RR disassemble(PrintStream ps) throws IOException, ExecutionException {
    ToolHelper.disassemble(app, ps);
    return self();
  }

  public RR disassemble() throws IOException, ExecutionException {
    return disassemble(System.out);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    appendInfo(builder);
    return builder.toString();
  }

  String errorMessage(String message) {
    return errorMessage(message, null);
  }

  String errorMessage(String message, String expected) {
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

  public RR writeProcessResult(PrintStream ps) {
    StringBuilder sb = new StringBuilder();
    appendProcessResult(sb);
    ps.println(sb.toString());
    return self();
  }
}
