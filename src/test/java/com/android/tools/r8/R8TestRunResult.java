// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class R8TestRunResult extends SingleTestRunResult<R8TestRunResult> {

  public interface GraphInspectorSupplier {
    GraphInspector get() throws IOException;
  }

  private final String proguardMap;
  private final GraphInspectorSupplier graphInspector;

  public R8TestRunResult(
      AndroidApp app,
      TestRuntime runtime,
      ProcessResult result,
      String proguardMap,
      GraphInspectorSupplier graphInspector,
      TestState state) {
    super(app, runtime, result, state);
    this.proguardMap = proguardMap;
    this.graphInspector = graphInspector;
  }

  @Override
  public boolean isR8TestRunResult() {
    return true;
  }

  @Override
  protected R8TestRunResult self() {
    return this;
  }

  @Override
  public StackTrace getStackTrace() {
    return super.getStackTrace().retraceAllowExperimentalMapping(proguardMap);
  }

  @Override
  protected CodeInspector internalGetCodeInspector() throws IOException {
    assertNotNull(app);
    return new CodeInspector(app, proguardMap);
  }

  public <E extends Throwable> R8TestRunResult inspectOriginalStackTrace(
      ThrowingBiConsumer<StackTrace, CodeInspector, E> consumer) throws E, IOException {
    consumer.accept(getOriginalStackTrace(), internalGetCodeInspector());
    return self();
  }

  public GraphInspector graphInspector() throws IOException, ExecutionException {
    assertSuccess();
    return graphInspector.get();
  }

  public R8TestRunResult inspectGraph(Consumer<GraphInspector> consumer)
      throws IOException, ExecutionException {
    consumer.accept(graphInspector());
    return self();
  }

  public <E extends Throwable> R8TestRunResult inspectStackTrace(
      ThrowingBiConsumer<StackTrace, CodeInspector, E> consumer) throws E, IOException {
    consumer.accept(getStackTrace(), internalGetCodeInspector());
    return self();
  }

  public String proguardMap() {
    return proguardMap;
  }
}
