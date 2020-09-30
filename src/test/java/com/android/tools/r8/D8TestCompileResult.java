// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;

public class D8TestCompileResult extends TestCompileResult<D8TestCompileResult, D8TestRunResult> {
  D8TestCompileResult(TestState state, AndroidApp app, int minApiLevel, OutputMode outputMode) {
    super(state, app, minApiLevel, outputMode);
  }

  @Override
  public D8TestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  @Override
  public String getStdout() {
    return state.getStdout();
  }

  @Override
  public String getStderr() {
    return state.getStderr();
  }

  @Override
  public D8TestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new D8TestRunResult(app, runtime, result);
  }
}
