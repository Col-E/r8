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
  private CollectingGraphConsumer graphConsumer = null;

  @Override
  R8TestBuilder self() {
    return this;
  }

  @Override
  R8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (enableInliningAnnotations || enableClassInliningAnnotations || enableMergeAnnotations) {
      ToolHelper.allowTestProguardOptions(builder);
    }
    StringBuilder proguardMapBuilder = new StringBuilder();
    builder.setProguardMapConsumer((string, ignore) -> proguardMapBuilder.append(string));
    ToolHelper.runR8WithoutResult(builder.build(), optionsConsumer);
    return new R8TestCompileResult(
        getState(), backend, app.get(), proguardMapBuilder.toString(), graphConsumer);
  }

  @Override
  public R8TestBuilder noTreeShaking() {
    builder.setDisableTreeShaking(true);
    return self();
  }

  @Override
  public R8TestBuilder noMinification() {
    builder.setDisableMinification(true);
    return self();
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
    builder.addProguardConfiguration(new ArrayList<>(rules), Origin.unknown());
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
      addKeepRules(
          "-forceinline class * { @com.android.tools.r8.ForceInline *; }",
          "-neverinline class * { @com.android.tools.r8.NeverInline *; }");
    }
    return self();
  }

  public R8TestBuilder enableClassInliningAnnotations() {
    if (!enableClassInliningAnnotations) {
      enableClassInliningAnnotations = true;
      addKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *");
    }
    return self();
  }

  public R8TestBuilder enableMergeAnnotations() {
    if (!enableMergeAnnotations) {
      enableMergeAnnotations = true;
      addKeepRules(
          "-nevermerge @com.android.tools.r8.NeverMerge class *");
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
}
