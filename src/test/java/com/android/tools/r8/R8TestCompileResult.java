// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class R8TestCompileResult extends TestCompileResult<R8TestCompileResult, R8TestRunResult> {

  private final ProguardConfiguration proguardConfiguration;
  private final List<ProguardConfigurationRule> syntheticProguardRules;
  private final String proguardMap;
  private final CollectingGraphConsumer graphConsumer;
  private final int minApiLevel;

  R8TestCompileResult(
      TestState state,
      OutputMode outputMode,
      AndroidApp app,
      ProguardConfiguration proguardConfiguration,
      List<ProguardConfigurationRule> syntheticProguardRules,
      String proguardMap,
      CollectingGraphConsumer graphConsumer,
      int minApiLevel) {
    super(state, app, outputMode);
    this.proguardConfiguration = proguardConfiguration;
    this.syntheticProguardRules = syntheticProguardRules;
    this.proguardMap = proguardMap;
    this.graphConsumer = graphConsumer;
    this.minApiLevel = minApiLevel;
  }

  @Override
  public R8TestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  public R8TestCompileResult inspectDiagnosticMessages(Consumer<TestDiagnosticMessages> consumer) {
    consumer.accept(state.getDiagnosticsMessages());
    return self();
  }

  @Override
  public String getStdout() {
    return state.getStdout();
  }

  @Override
  public String getStderr() {
    return state.getStderr();
  }

  @Override
  public CodeInspector inspector() throws IOException {
    return new CodeInspector(app, proguardMap);
  }

  public GraphInspector graphInspector() throws IOException {
    assert graphConsumer != null;
    return new GraphInspector(graphConsumer, inspector());
  }

  public ProguardConfiguration getProguardConfiguration() {
    return proguardConfiguration;
  }

  public R8TestCompileResult inspectProguardConfiguration(
      Consumer<ProguardConfiguration> consumer) {
    consumer.accept(getProguardConfiguration());
    return self();
  }

  public List<ProguardConfigurationRule> getSyntheticProguardRules() {
    return syntheticProguardRules;
  }

  public R8TestCompileResult inspectSyntheticProguardRules(
      Consumer<List<ProguardConfigurationRule>> consumer) {
    consumer.accept(getSyntheticProguardRules());
    return self();
  }

  @Override
  public R8TestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new R8TestRunResult(app, runtime, result, proguardMap, this::graphInspector);
  }

  public String getProguardMap() {
    return proguardMap;
  }

  public R8TestCompileResult writeProguardMap(Path path) throws IOException {
    FileUtils.writeTextFile(path, getProguardMap());
    return self();
  }
}
