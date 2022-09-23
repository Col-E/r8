// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
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

public class ProguardTestCompileResult
    extends TestCompileResult<ProguardTestCompileResult, ProguardTestRunResult> {

  private final ProcessResult result;
  private final Path outputJar;
  private final String proguardMap;

  ProguardTestCompileResult(
      ProcessResult result, TestState state, Path outputJar, int minApiLevel, String proguardMap) {
    super(
        state,
        AndroidApp.builder().addProgramFiles(outputJar).build(),
        minApiLevel,
        OutputMode.ClassFile);
    this.result = result;
    this.outputJar = outputJar;
    this.proguardMap = proguardMap;
  }

  public Path outputJar() {
    return outputJar;
  }

  @Override
  public ProguardTestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    throw new UnsupportedOperationException("No diagnostics messages from Proguard");
  }

  @Override
  public Set<String> getMainDexClasses() {
    throw new Unimplemented();
  }

  @Override
  public String getStdout() {
    return result.stdout;
  }

  @Override
  public String getStderr() {
    return result.stderr;
  }

  @Override
  public CodeInspector inspector() throws IOException {
    return new CodeInspector(app, proguardMap);
  }

  @Override
  public ProguardTestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new ProguardTestRunResult(app, runtime, result, proguardMap, state);
  }
}
