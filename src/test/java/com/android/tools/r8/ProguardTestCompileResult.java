// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ProguardTestCompileResult extends TestCompileResult<ProguardTestRunResult> {

  private final String proguardMap;

  ProguardTestCompileResult(TestState state, AndroidApp app, String proguardMap) {
    super(state, app);
    this.proguardMap = proguardMap;
  }

  @Override
  public Backend getBackend() {
    return Backend.CF;
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app, proguardMap);
  }

  @Override
  public ProguardTestRunResult createRunResult(AndroidApp app, ProcessResult result) {
    return new ProguardTestRunResult(app, result, proguardMap);
  }
}
