// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApp;
import java.io.IOException;
import java.nio.file.Path;

public class TestCompileResult {
  private final TestState state;
  private final Backend backend;
  private final AndroidApp app;

  public TestCompileResult(TestState state, Backend backend, AndroidApp app) {
    this.state = state;
    this.backend = backend;
    this.app = app;
  }

  public TestRunResult run(String mainClass) throws IOException {
    switch (backend) {
      case DEX:
        return runArt(mainClass);
      case CF:
        return runJava(mainClass);
      default:
        throw new Unreachable();
    }
  }

  private TestRunResult runJava(String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.ClassFile);
    ProcessResult result = ToolHelper.runJava(out, mainClass);
    return new TestRunResult(app, result);
  }

  private TestRunResult runArt(String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.DexIndexed);
    ProcessResult result = ToolHelper.runArtRaw(out.toString(), mainClass);
    return new TestRunResult(app, result);
  }
}
