// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class D8TestCompileResult extends TestCompileResult<D8TestCompileResult, D8TestRunResult> {

  private final String proguardMap;
  private final List<ExternalArtProfile> residualArtProfiles;

  D8TestCompileResult(
      TestState state,
      AndroidApp app,
      int minApiLevel,
      OutputMode outputMode,
      LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration,
      String proguardMap,
      List<ExternalArtProfile> residualArtProfiles) {
    super(state, app, minApiLevel, outputMode, libraryDesugaringTestConfiguration);
    this.proguardMap = proguardMap;
    this.residualArtProfiles = residualArtProfiles;
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

  public <E extends Throwable> D8TestCompileResult inspectResidualArtProfile(
      ThrowingConsumer<ArtProfileInspector, E> consumer) throws E, IOException {
    return inspectResidualArtProfile(
        (rewrittenArtProfile, inspector) -> consumer.accept(rewrittenArtProfile));
  }

  public <E extends Throwable> D8TestCompileResult inspectResidualArtProfile(
      ThrowingBiConsumer<ArtProfileInspector, CodeInspector, E> consumer) throws E, IOException {
    assertEquals(1, residualArtProfiles.size());
    consumer.accept(new ArtProfileInspector(residualArtProfiles.iterator().next()), inspector());
    return self();
  }
}
