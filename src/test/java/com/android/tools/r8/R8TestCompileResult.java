// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.ResourceTableInspector;
import com.android.tools.r8.dexsplitter.SplitterTestBase.SplitRunner;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class R8TestCompileResult extends TestCompileResult<R8TestCompileResult, R8TestRunResult> {

  private final ProguardConfiguration proguardConfiguration;
  private final List<ProguardConfigurationRule> syntheticProguardRules;
  private final String proguardMap;
  private final CollectingGraphConsumer graphConsumer;
  private final List<Path> features;
  private final List<ExternalArtProfile> residualArtProfiles;
  private final Path resourceShrinkerOutput;

  R8TestCompileResult(
      TestState state,
      OutputMode outputMode,
      LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration,
      AndroidApp app,
      ProguardConfiguration proguardConfiguration,
      List<ProguardConfigurationRule> syntheticProguardRules,
      String proguardMap,
      CollectingGraphConsumer graphConsumer,
      int minApiLevel,
      List<Path> features,
      List<ExternalArtProfile> residualArtProfiles,
      Path resourceShrinkerOutput) {
    super(state, app, minApiLevel, outputMode, libraryDesugaringTestConfiguration);
    this.proguardConfiguration = proguardConfiguration;
    this.syntheticProguardRules = syntheticProguardRules;
    this.proguardMap = proguardMap;
    this.graphConsumer = graphConsumer;
    this.features = features;
    this.residualArtProfiles = residualArtProfiles;
    this.resourceShrinkerOutput = resourceShrinkerOutput;
  }

  @Override
  public R8TestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  @Override
  public R8TestCompileResult inspectDiagnosticMessages(Consumer<TestDiagnosticMessages> consumer) {
    consumer.accept(state.getDiagnosticsMessages());
    return self();
  }

  public Path getFeature(int index) {
    return features.get(index);
  }

  public List<Path> getFeatures() {
    return features;
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

  @Override
  public CodeInspector inspector() throws IOException {
    return inspector(null);
  }

  public CodeInspector inspector(Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException {
    return new CodeInspector(app, proguardMap, debugOptionsConsumer);
  }

  private CodeInspector featureInspector(Path feature) throws IOException {
    return new CodeInspector(
        AndroidApp.builder().addProgramFile(feature).setProguardMapOutputData(proguardMap).build());
  }

  @SafeVarargs
  public final <E extends Throwable> R8TestCompileResult inspect(
      ThrowingConsumer<CodeInspector, E>... consumers) throws IOException, E {
    assertEquals(1 + features.size(), consumers.length);
    consumers[0].accept(inspector());
    for (int i = 0; i < features.size(); i++) {
      consumers[i + 1].accept(featureInspector(features.get(i)));
    }
    return self();
  }

  @SafeVarargs
  @Override
  public final <E extends Throwable> R8TestCompileResult inspectMultiDex(
      ThrowingConsumer<CodeInspector, E>... consumers) throws IOException, E {
    return inspectMultiDex(writeProguardMap(), consumers);
  }

  public final <E extends Throwable> R8TestCompileResult inspectGraph(
      ThrowingConsumer<GraphInspector, E> consumer) throws IOException, E {
    consumer.accept(graphInspector());
    return self();
  }

  public <E extends Throwable> R8TestCompileResult inspectResidualArtProfile(
      ThrowingConsumer<ArtProfileInspector, E> consumer) throws E, IOException {
    return inspectResidualArtProfile(
        (rewrittenArtProfile, inspector) -> consumer.accept(rewrittenArtProfile));
  }

  public <E extends Throwable> R8TestCompileResult inspectResidualArtProfile(
      ThrowingBiConsumer<ArtProfileInspector, CodeInspector, E> consumer) throws E, IOException {
    assertEquals(1, residualArtProfiles.size());
    consumer.accept(new ArtProfileInspector(residualArtProfiles.iterator().next()), inspector());
    return self();
  }

  public <E extends Throwable> R8TestCompileResult inspectShrunkenResources(
      Consumer<ResourceTableInspector> consumer) throws IOException {
    assertNotNull(resourceShrinkerOutput);
    consumer.accept(
        new ResourceTableInspector(
            ZipUtils.readSingleEntry(resourceShrinkerOutput, "resources.pb")));
    return self();
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
    return new R8TestRunResult(app, runtime, result, proguardMap, this::graphInspector, state);
  }

  public R8TestCompileResult addFeatureSplitsToRunClasspathFiles() {
    return addRunClasspathFiles(features);
  }

  public R8TestRunResult runFeature(TestRuntime runtime, Class<?> mainFeatureClass)
      throws IOException {
    return runFeature(runtime, mainFeatureClass, features.get(0));
  }

  public R8TestRunResult runFeature(
      TestRuntime runtime, Class<?> mainFeatureClass, Path feature, Path... featureDependencies)
      throws IOException {
    assert getBackend() == runtime.getBackend();
    ClassSubject mainClassSubject = inspector().clazz(SplitRunner.class);
    assertThat("Did you forget a keep rule for the main method?", mainClassSubject, isPresent());
    assertThat(
        "Did you forget a keep rule for the main method?",
        mainClassSubject.mainMethod(),
        isPresent());
    ClassSubject mainFeatureClassSubject = featureInspector(feature).clazz(mainFeatureClass);
    assertThat(
        "Did you forget a keep rule for the run method?", mainFeatureClassSubject, isPresent());
    assertThat(
        "Did you forget a keep rule for the run method?",
        mainFeatureClassSubject.uniqueMethodWithOriginalName("run"),
        isPresent());
    String[] args = new String[2 + featureDependencies.length];
    args[0] = mainFeatureClassSubject.getFinalName();
    args[1] = feature.toString();
    for (int i = 2; i < args.length; i++) {
      args[i] = featureDependencies[i - 2].toString();
    }
    return runArt(runtime, mainClassSubject.getFinalName(), args);
  }

  public String getProguardMap() {
    return proguardMap;
  }

  public R8TestCompileResult inspectProguardMap(ThrowableConsumer<String> consumer)
      throws Throwable {
    consumer.accept(getProguardMap());
    return this;
  }

  public Path writeProguardMap() throws IOException {
    Path file = state.getNewTempFolder().resolve("out.zip");
    writeProguardMap(file);
    return file;
  }

  public R8TestCompileResult writeProguardMap(Path path) throws IOException {
    FileUtils.writeTextFile(path, getProguardMap());
    return self();
  }
}
