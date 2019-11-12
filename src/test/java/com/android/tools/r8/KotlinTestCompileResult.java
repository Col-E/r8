// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;

public class KotlinTestCompileResult
    extends TestCompileResult<KotlinTestCompileResult, KotlinTestRunResult> {

  private final Path outputJar;
  private final ProcessResult processResult;

  KotlinTestCompileResult(TestState state, Path outputJar, ProcessResult processResult) {
    super(state, AndroidApp.builder().addProgramFile(outputJar).build(), OutputMode.ClassFile);
    this.outputJar = outputJar;
    this.processResult = processResult;
  }

  public Path outputJar() {
    return outputJar;
  }

  public int exitCode() {
    return processResult.exitCode;
  }

  public String stdout() {
    return processResult.stdout;
  }

  public String stderr() {
    return processResult.stderr;
  }

  @Override
  public KotlinTestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    throw new UnsupportedOperationException("No diagnostics messages from kotlinc");
  }

  @Override
  public KotlinTestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new KotlinTestRunResult(app, runtime, result);
  }
}
