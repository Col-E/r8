// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestBase.R8Mode;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class R8TestBuilder
    extends TestShrinkerBuilder<
        R8Command, Builder, R8TestCompileResult, R8TestRunResult, R8TestBuilder> {

  private R8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static R8TestBuilder create(TestState state, Backend backend, R8Mode mode) {
    R8Command.Builder builder =
        mode == R8Mode.Full
            ? R8Command.builder(state.getDiagnosticsHandler())
            : new CompatProguardCommandBuilder(true, state.getDiagnosticsHandler());
    return new R8TestBuilder(state, builder, backend);
  }

  private boolean enableInliningAnnotations = false;
  private boolean enableClassInliningAnnotations = false;
  private boolean enableMergeAnnotations = false;
  private boolean enableConstantArgumentAnnotations = false;
  private boolean enableUnusedArgumentAnnotations = false;
  private CollectingGraphConsumer graphConsumer = null;
  private List<String> keepRules = new ArrayList<>();

  @Override
  R8TestBuilder self() {
    return this;
  }

  @Override
  R8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (enableInliningAnnotations
        || enableClassInliningAnnotations
        || enableMergeAnnotations
        || enableConstantArgumentAnnotations
        || enableUnusedArgumentAnnotations) {
      ToolHelper.allowTestProguardOptions(builder);
    }
    if (!keepRules.isEmpty()) {
      builder.addProguardConfiguration(keepRules, Origin.unknown());
    }
    StringBuilder proguardMapBuilder = new StringBuilder();
    builder.setDisableTreeShaking(!enableTreeShaking);
    builder.setDisableMinification(!enableMinification);
    builder.setProguardMapConsumer((string, ignore) -> proguardMapBuilder.append(string));
    ToolHelper.runR8WithoutResult(builder.build(), optionsConsumer);
    return new R8TestCompileResult(
        getState(), backend, app.get(), proguardMapBuilder.toString(), graphConsumer);
  }

  public R8TestBuilder addDataResources(List<DataEntryResource> resources) {
    resources.forEach(builder.getAppBuilder()::addDataResource);
    return self();
  }

  @Override
  public R8TestBuilder addKeepRuleFiles(List<Path> files) {
    builder.addProguardConfigurationFiles(files);
    return self();
  }

  @Override
  public R8TestBuilder addKeepRules(Collection<String> rules) {
    // Delay adding the actual rules so that we only associate a single origin and unique lines to
    // each actual rule.
    keepRules.addAll(rules);
    return self();
  }

  public R8TestBuilder addMainDexRules(Collection<String> rules) {
    builder.addMainDexRules(new ArrayList<>(rules), Origin.unknown());
    return self();
  }

  public R8TestBuilder addMainDexRules(String... rules) {
    return addMainDexRules(Arrays.asList(rules));
  }

  public R8TestBuilder addMainDexClassRules(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addMainDexRules("-keep class " + clazz.getTypeName());
    }
    return self();
  }

  public R8TestBuilder addMainDexListClasses(Class<?>... classes) {
    builder.addMainDexClasses(
        Arrays.stream(classes).map(Class::getTypeName).collect(Collectors.toList()));
    return self();
  }

  public R8TestBuilder enableInliningAnnotations() {
    if (!enableInliningAnnotations) {
      enableInliningAnnotations = true;
      addInternalKeepRules(
          "-forceinline class * { @com.android.tools.r8.ForceInline *; }",
          "-neverinline class * { @com.android.tools.r8.NeverInline *; }");
    }
    return self();
  }

  public R8TestBuilder enableClassInliningAnnotations() {
    if (!enableClassInliningAnnotations) {
      enableClassInliningAnnotations = true;
      addInternalKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *");
    }
    return self();
  }

  public R8TestBuilder enableMergeAnnotations() {
    if (!enableMergeAnnotations) {
      enableMergeAnnotations = true;
      addInternalKeepRules("-nevermerge @com.android.tools.r8.NeverMerge class *");
    }
    return self();
  }

  public R8TestBuilder enableConstantArgumentAnnotations() {
    if (!enableConstantArgumentAnnotations) {
      enableConstantArgumentAnnotations = true;
      addInternalKeepRules(
          "-keepconstantarguments class * { @com.android.tools.r8.KeepConstantArguments *; }");
    }
    return self();
  }

  public R8TestBuilder enableUnusedArgumentAnnotations() {
    if (!enableUnusedArgumentAnnotations) {
      enableUnusedArgumentAnnotations = true;
      addInternalKeepRules(
          "-keepunusedarguments class * { @com.android.tools.r8.KeepUnusedArguments *; }");
    }
    return self();
  }

  public R8TestBuilder enableProguardTestOptions() {
    builder.allowTestProguardOptions();
    return self();
  }

  public R8TestBuilder enableGraphInspector() {
    CollectingGraphConsumer consumer = new CollectingGraphConsumer(null);
    setKeptGraphConsumer(consumer);
    graphConsumer = consumer;
    return self();
  }

  public R8TestBuilder setKeptGraphConsumer(GraphConsumer graphConsumer) {
    assert this.graphConsumer == null;
    builder.setKeptGraphConsumer(graphConsumer);
    return self();
  }

  public R8TestBuilder setMainDexKeptGraphConsumer(GraphConsumer graphConsumer) {
    builder.setMainDexKeptGraphConsumer(graphConsumer);
    return self();
  }

  private void addInternalKeepRules(String... rules) {
    // We don't add these to the keep-rule set for other test provided rules.
    builder.addProguardConfiguration(Arrays.asList(rules), Origin.unknown());
  }
}
