// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class R8TestCompileResult extends TestCompileResult<R8TestCompileResult, R8TestRunResult> {

  private final Backend backend;
  private final String proguardMap;
  private final CollectingGraphConsumer graphConsumer;

  R8TestCompileResult(
      TestState state,
      Backend backend,
      AndroidApp app,
      String proguardMap,
      CollectingGraphConsumer graphConsumer) {
    super(state, app);
    this.backend = backend;
    this.proguardMap = proguardMap;
    this.graphConsumer = graphConsumer;
  }

  @Override
  public R8TestCompileResult self() {
    return this;
  }

  @Override
  public Backend getBackend() {
    return backend;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app, proguardMap);
  }

  public GraphInspector graphInspector() throws IOException, ExecutionException {
    assert graphConsumer != null;
    return new GraphInspector(graphConsumer, inspector());
  }

  @Override
  public R8TestRunResult createRunResult(ProcessResult result) {
    return new R8TestRunResult(app, result, proguardMap, this::graphInspector);
  }
}
