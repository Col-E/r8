// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;

public class D8TestRunResult extends SingleTestRunResult<D8TestRunResult> {

  private final String proguardMap;

  public D8TestRunResult(
      AndroidApp app,
      TestRuntime runtime,
      ProcessResult result,
      String proguardMap,
      TestState state) {
    super(app, runtime, result, state);
    this.proguardMap = proguardMap;
  }

  @Override
  protected D8TestRunResult self() {
    return this;
  }

  @Override
  protected CodeInspector internalGetCodeInspector() throws IOException {
    assertNotNull(app);
    return proguardMap == null ? new CodeInspector(app) : new CodeInspector(app, proguardMap);
  }

  @Override
  public StackTrace getStackTrace() {
    if (proguardMap == null) {
      return super.getStackTrace();
    }
    return super.getStackTrace().retraceAllowExperimentalMapping(proguardMap);
  }
}
