// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class ExternalR8TestCompileResult
    extends TestCompileResult<ExternalR8TestCompileResult, ExternalR8TestRunResult> {

  private final Path outputJar;
  private final ProcessResult processResult;
  private final String proguardMap;

  protected ExternalR8TestCompileResult(
      TestState state, Path outputJar, ProcessResult processResult, String proguardMap) {
    super(state, AndroidApp.builder().addProgramFiles(outputJar).build());
    assert processResult.exitCode == 0;
    this.outputJar = outputJar;
    this.processResult = processResult;
    this.proguardMap = proguardMap;
  }

  public Path outputJar() {
    return outputJar;
  }

  public String stdout() {
    return processResult.stdout;
  }

  public String stderr() {
    return processResult.stdout;
  }

  @Override
  public ExternalR8TestCompileResult self() {
    return this;
  }

  @Override
  public Backend getBackend() {
    return null;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    throw new UnsupportedOperationException("No diagnostics messages from external R8");
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app, proguardMap);
  }

  @Override
  protected ExternalR8TestRunResult createRunResult(ProcessResult result) {
    return new ExternalR8TestRunResult(app, outputJar, proguardMap, result);
  }
}
