// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class L8TestCompileResult extends TestCompileResult<L8TestCompileResult, L8TestRunResult> {

  private final List<String> allKeepRules;
  private final String generatedKeepRules;
  private final Path mapping;

  public L8TestCompileResult(
      AndroidApp app,
      AndroidApiLevel apiLevel,
      List<String> allKeepRules,
      String generatedKeepRules,
      Path mapping,
      TestState state,
      OutputMode outputMode) {
    super(state, app, apiLevel.getLevel(), outputMode);
    this.allKeepRules = allKeepRules;
    this.generatedKeepRules = generatedKeepRules;
    this.mapping = mapping;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  @Override
  public Set<String> getMainDexClasses() {
    throw new Unimplemented();
  }

  @Override
  public String getStdout() {
    throw new Unimplemented();
  }

  @Override
  public String getStderr() {
    throw new Unimplemented();
  }

  @Override
  protected L8TestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    throw new Unimplemented();
  }

  @Override
  public CodeInspector inspector() throws IOException {
    return mapping != null && mapping.toFile().exists()
        ? new CodeInspector(app, mapping)
        : super.inspector();
  }

  @Override
  public L8TestCompileResult self() {
    return this;
  }

  public <E extends Throwable> L8TestCompileResult inspectKeepRules(
      ThrowingConsumer<List<String>, E> consumer) throws E {
    consumer.accept(allKeepRules);
    return self();
  }

  public L8TestCompileResult writeGeneratedKeepRules(Path path) throws IOException {
    assertNotNull(generatedKeepRules);
    FileUtils.writeTextFile(path, generatedKeepRules);
    return self();
  }

  public L8TestCompileResult writeProguardMap(Path path) throws IOException {
    assertNotNull(mapping);
    Files.copy(mapping, path);
    return self();
  }
}
