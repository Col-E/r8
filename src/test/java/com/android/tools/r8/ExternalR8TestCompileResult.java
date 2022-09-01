// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class ExternalR8TestCompileResult
    extends TestCompileResult<ExternalR8TestCompileResult, ExternalR8TestRunResult> {

  private final Path outputJar;
  private final ProcessResult processResult;
  private final String proguardMap;

  protected ExternalR8TestCompileResult(
      TestState state,
      Path outputJar,
      ProcessResult processResult,
      String proguardMap,
      int minApiLevel,
      OutputMode outputMode) {
    super(state, AndroidApp.builder().addProgramFiles(outputJar).build(), minApiLevel, outputMode);
    assert processResult.exitCode == 0;
    this.outputJar = outputJar;
    this.processResult = processResult;
    this.proguardMap = proguardMap;
  }

  public Path outputJar() {
    return outputJar;
  }

  public String getProguardMap() {
    return proguardMap;
  }

  @Override
  public ExternalR8TestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    throw new UnsupportedOperationException("No diagnostics messages from external R8");
  }

  @Override
  public Set<String> getMainDexClasses() {
    throw new Unimplemented();
  }

  @Override
  public String getStdout() {
    return processResult.stdout;
  }

  @Override
  public String getStderr() {
    return processResult.stderr;
  }

  @Override
  public CodeInspector inspector() throws IOException {
    return new CodeInspector(app, proguardMap);
  }

  @Override
  protected ExternalR8TestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new ExternalR8TestRunResult(app, outputJar, proguardMap, runtime, result, state);
  }
}
