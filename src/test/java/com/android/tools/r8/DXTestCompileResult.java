// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import java.util.Set;

public class DXTestCompileResult extends TestCompileResult<DXTestCompileResult, DXTestRunResult> {

  DXTestCompileResult(TestState state, AndroidApp app, int minApiLevel) {
    super(state, app, minApiLevel, OutputMode.DexIndexed);
  }

  @Override
  public DXTestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    throw new UnsupportedOperationException("No diagnostics messages from dx");
  }

  @Override
  public Set<String> getMainDexClasses() {
    throw new Unimplemented();
  }

  @Override
  public String getStdout() {
    throw new Unimplemented("Unexpected attempt to access stdout from dx");
  }

  @Override
  public String getStderr() {
    throw new Unimplemented("Unexpected attempt to access stderr from dx");
  }

  @Override
  public DXTestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new DXTestRunResult(app, runtime, result, state);
  }
}
