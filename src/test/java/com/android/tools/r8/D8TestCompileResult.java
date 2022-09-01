// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import java.util.Set;

public class D8TestCompileResult extends TestCompileResult<D8TestCompileResult, D8TestRunResult> {
  private final String proguardMap;

  D8TestCompileResult(
      TestState state,
      AndroidApp app,
      int minApiLevel,
      OutputMode outputMode,
      LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration,
      String proguardMap) {
    super(state, app, minApiLevel, outputMode, libraryDesugaringTestConfiguration);
    this.proguardMap = proguardMap;
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
  public Set<String> getMainDexClasses() {
    return state.getMainDexClasses();
  }

  @Override
  public String getStdout() {
    return state.getStdout();
  }

  @Override
  public String getStderr() {
    return state.getStderr();
  }

  public String getProguardMap() {
    return proguardMap;
  }

  @Override
  public D8TestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new D8TestRunResult(app, runtime, result, proguardMap, state);
  }
}
