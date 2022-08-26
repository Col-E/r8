// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class D8TestBuilder
    extends TestCompilerBuilder<
        D8Command, Builder, D8TestCompileResult, D8TestRunResult, D8TestBuilder> {

  private D8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static D8TestBuilder create(TestState state, Backend backend) {
    return new D8TestBuilder(state, D8Command.builder(state.getDiagnosticsHandler()), backend);
  }

  private StringBuilder proguardMapOutputBuilder = null;

  @Override
  public boolean isD8TestBuilder() {
    return true;
  }

  @Override
  public D8TestBuilder asD8TestBuilder() {
    return this;
  }

  @Override
  D8TestBuilder self() {
    return this;
  }

  public Builder getBuilder() {
    return builder;
  }

  public D8TestBuilder addProgramResourceProviders(Collection<ProgramResourceProvider> providers) {
    for (ProgramResourceProvider provider : providers) {
      builder.addProgramResourceProvider(provider);
    }
    return self();
  }

  public D8TestBuilder addProgramResourceProviders(ProgramResourceProvider... providers) {
    return addProgramResourceProviders(Arrays.asList(providers));
  }

  @Override
  public D8TestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    builder.addClasspathResourceProvider(ClassFileResourceProviderFromClasses(classes));
    return self();
  }

  @Override
  public D8TestBuilder addClasspathFiles(Collection<Path> files) {
    builder.addClasspathFiles(files);
    return self();
  }

  @Override
  D8TestCompileResult internalCompile(
      Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException {
    libraryDesugaringTestConfiguration.configure(builder);
    ToolHelper.runAndBenchmarkD8(builder, optionsConsumer, benchmarkResults);
    return new D8TestCompileResult(
        getState(),
        app.get(),
        getMinApiLevel(),
        getOutputMode(),
        libraryDesugaringTestConfiguration,
        getMapContent());
  }

  private String getMapContent() {
    return proguardMapOutputBuilder == null ? null : proguardMapOutputBuilder.toString();
  }

  public D8TestBuilder setIntermediate(boolean intermediate) {
    builder.setIntermediate(intermediate);
    return self();
  }

  public D8TestBuilder addMainDexRulesFiles(Path... mainDexRuleFiles) {
    builder.addMainDexRulesFiles(mainDexRuleFiles);
    return self();
  }

  public D8TestBuilder addMainDexRules(String... rules) {
    builder.addMainDexRules(Arrays.asList(rules), Origin.unknown());
    return self();
  }

  public D8TestBuilder addMainDexKeepClassRules(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addMainDexRules("-keep class " + clazz.getTypeName());
    }
    return self();
  }

  public D8TestBuilder addMainDexKeepClassAndMemberRules(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addMainDexRules("-keep class " + clazz.getTypeName() + " { *; }");
    }
    return self();
  }

  public D8TestBuilder internalEnableMappingOutput() {
    assert proguardMapOutputBuilder == null;
    proguardMapOutputBuilder = new StringBuilder();
    getBuilder().setProguardMapConsumer((s, h) -> proguardMapOutputBuilder.append(s));
    return self();
  }

  public D8TestBuilder addStartupProfileProviders(
      StartupProfileProvider... startupProfileProviders) {
    builder.addStartupProfileProviders(startupProfileProviders);
    return self();
  }

  public D8TestBuilder addStartupProfileProviders(
      Collection<StartupProfileProvider> startupProfileProviders) {
    builder.addStartupProfileProviders(startupProfileProviders);
    return self();
  }
}
