// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.hamcrest.Matcher;

public abstract class SingleTestRunResult<RR extends SingleTestRunResult<RR>>
    extends TestRunResult<RR> {
  private final TestState state;
  protected final AndroidApp app;
  private final TestRuntime runtime;
  private final ProcessResult result;
  private boolean executedSatisfyingRuntime = false;

  public SingleTestRunResult(
      AndroidApp app, TestRuntime runtime, ProcessResult result, TestState state) {
    this.state = state;
    this.app = app;
    this.runtime = runtime;
    this.result = result;
  }

  public boolean isR8TestRunResult() {
    return false;
  }

  public TestState getState() {
    return state;
  }

  public AndroidApp app() {
    return app;
  }

  public ProcessResult getResult() {
    return result;
  }

  public String getStdOut() {
    return result.stdout;
  }

  public <E extends Throwable> RR inspectStdOut(ThrowingConsumer<String, E> consumer) throws E {
    consumer.accept(getStdOut());
    return self();
  }

  public String getStdErr() {
    return result.stderr;
  }

  public StackTrace getStackTrace() {
    return getOriginalStackTrace();
  }

  public StackTrace getOriginalStackTrace() {
    if (runtime.isDex()) {
      return StackTrace.extractFromArt(getStdErr(), runtime.asDex().getVm());
    } else {
      return StackTrace.extractFromJvm(getStdErr());
    }
  }

  public int getExitCode() {
    return result.exitCode;
  }

  @Override
  public RR assertSuccess() {
    assertEquals(errorMessage("Expected run to succeed."), 0, result.exitCode);
    return self();
  }

  @Override
  public RR assertStdoutMatches(Matcher<String> matcher) {
    assertThat(errorMessage("Run stdout incorrect.", matcher.toString()), result.stdout, matcher);
    return self();
  }

  @Override
  public RR assertFailure() {
    assertNotEquals(errorMessage("Expected run to fail."), 0, result.exitCode);
    return self();
  }

  @Override
  public RR assertStderrMatches(Matcher<String> matcher) {
    assertThat(errorMessage("Run stderr incorrect.", matcher.toString()), result.stderr, matcher);
    return self();
  }

  protected CodeInspector internalGetCodeInspector() throws IOException {
    assertNotNull(app);
    return new CodeInspector(app);
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    // Inspection post run implies success. If inspection of an invalid program is needed it should
    // be done on the compilation result or on the input.
    assertSuccess();
    return internalGetCodeInspector();
  }

  @Override
  public <E extends Throwable> RR inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E {
    CodeInspector inspector = inspector();
    consumer.accept(inspector);
    return self();
  }

  @Override
  public <E extends Throwable> RR inspectFailure(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, E {
    assertFailure();
    CodeInspector inspector = internalGetCodeInspector();
    consumer.accept(inspector);
    return self();
  }

  public <E extends Throwable> RR inspectStackTrace(ThrowingConsumer<StackTrace, E> consumer)
      throws E {
    consumer.accept(getStackTrace());
    return self();
  }

  public <E extends Throwable> RR inspectOriginalStackTrace(
      ThrowingConsumer<StackTrace, E> consumer) throws E {
    consumer.accept(getOriginalStackTrace());
    return self();
  }

  public RR disassemble(PrintStream ps) throws IOException, ExecutionException {
    ToolHelper.disassemble(app, ps);
    return self();
  }

  @Override
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

  public RR forDexRuntimeSatisfying(Predicate<DexVm.Version> predicate, Consumer<RR> action) {
    if (runtime.isDex() && predicate.test(runtime.asDex().getVm().getVersion())) {
      action.accept(self());
      executedSatisfyingRuntime = true;
    }
    return self();
  }

  public RR forCfRuntime(Consumer<RR> action) {
    if (runtime.isCf()) {
      action.accept(self());
      executedSatisfyingRuntime = true;
    }
    return self();
  }

  public RR otherwise(Consumer<RR> action) {
    if (!executedSatisfyingRuntime) {
      action.accept(self());
    }
    return self();
  }

  public <E extends Throwable> RR debugger(ThrowingConsumer<DebugTestConfig, E> consumer)
      throws E, IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, runtime.isCf() ? OutputMode.ClassFile : OutputMode.DexIndexed);
    DebugTestConfig config = DebugTestConfig.create(runtime, out);
    consumer.accept(config);
    return self();
  }
}
