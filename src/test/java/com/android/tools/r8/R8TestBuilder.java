// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.desugar.corelib.CoreLibDesugarTestBase.KeepRuleConsumer;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class R8TestBuilder<T extends R8TestBuilder<T>>
    extends TestShrinkerBuilder<R8Command, Builder, R8TestCompileResult, R8TestRunResult, T> {

  R8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  private boolean enableInliningAnnotations = false;
  private boolean enableClassInliningAnnotations = false;
  private boolean enableMergeAnnotations = false;
  private boolean enableMemberValuePropagationAnnotations = false;
  private boolean enableConstantArgumentAnnotations = false;
  private boolean enableUnusedArgumentAnnotations = false;
  private boolean enableSideEffectAnnotations = false;
  private CollectingGraphConsumer graphConsumer = null;
  private List<String> keepRules = new ArrayList<>();
  private List<Path> mainDexRulesFiles = new ArrayList<>();
  private List<String> applyMappingMaps = new ArrayList<>();

  @Override
  R8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (enableInliningAnnotations
        || enableClassInliningAnnotations
        || enableMergeAnnotations
        || enableMemberValuePropagationAnnotations
        || enableConstantArgumentAnnotations
        || enableUnusedArgumentAnnotations
        || enableSideEffectAnnotations) {
      ToolHelper.allowTestProguardOptions(builder);
    }
    if (!keepRules.isEmpty()) {
      builder.addProguardConfiguration(keepRules, Origin.unknown());
    }
    builder.addMainDexRulesFiles(mainDexRulesFiles);
    StringBuilder proguardMapBuilder = new StringBuilder();
    builder.setDisableTreeShaking(!enableTreeShaking);
    builder.setDisableMinification(!enableMinification);
    builder.setProguardMapConsumer((string, ignore) -> proguardMapBuilder.append(string));

    if (!applyMappingMaps.isEmpty()) {
      try {
        Path mappingsDir = getState().getNewTempFolder();
        for (int i = 0; i < applyMappingMaps.size(); i++) {
          String mapContent = applyMappingMaps.get(i);
          Path mapPath = mappingsDir.resolve("mapping" + i + ".map");
          FileUtils.writeTextFile(mapPath, mapContent);
          builder.addProguardConfiguration(
              Collections.singletonList("-applymapping " + mapPath.toString()), Origin.unknown());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    class Box {
      private List<ProguardConfigurationRule> syntheticProguardRules;
      private ProguardConfiguration proguardConfiguration;
    }
    Box box = new Box();
    ToolHelper.addSyntheticProguardRulesConsumerForTesting(
        builder, rules -> box.syntheticProguardRules = rules);
    ToolHelper.runR8WithoutResult(
        builder.build(),
        optionsConsumer.andThen(
            options -> box.proguardConfiguration = options.getProguardConfiguration()));
    return new R8TestCompileResult(
        getState(),
        getOutputMode(),
        app.get(),
        box.proguardConfiguration,
        box.syntheticProguardRules,
        proguardMapBuilder.toString(),
        graphConsumer);
  }

  public Builder getBuilder() {
    return builder;
  }

  public T addProgramResourceProvider(ProgramResourceProvider provider) {
    builder.addProgramResourceProvider(provider);
    return self();
  }

  @Override
  public T addClasspathClasses(Collection<Class<?>> classes) {
    builder.addClasspathResourceProvider(ClassFileResourceProviderFromClasses(classes));
    return self();
  }

  @Override
  public T addClasspathFiles(Collection<Path> files) {
    builder.addClasspathFiles(files);
    return self();
  }

  public T addDataResources(List<DataEntryResource> resources) {
    resources.forEach(builder.getAppBuilder()::addDataResource);
    return self();
  }

  @Override
  public T addDataEntryResources(DataEntryResource... resources) {
    return addDataResources(Arrays.asList(resources));
  }

  @Override
  public T addKeepRuleFiles(List<Path> files) {
    builder.addProguardConfigurationFiles(files);
    return self();
  }

  @Override
  public T addKeepRules(Collection<String> rules) {
    // Delay adding the actual rules so that we only associate a single origin and unique lines to
    // each actual rule.
    keepRules.addAll(rules);
    return self();
  }

  public T addMainDexRules(Collection<String> rules) {
    builder.addMainDexRules(new ArrayList<>(rules), Origin.unknown());
    return self();
  }

  public T addMainDexRules(String... rules) {
    return addMainDexRules(Arrays.asList(rules));
  }

  public T addMainDexRuleFiles(List<Path> files) {
    mainDexRulesFiles.addAll(files);
    return self();
  }

  public T addMainDexRuleFiles(Path... files) {
    return addMainDexRuleFiles(Arrays.asList(files));
  }

  public T addMainDexClassRules(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addMainDexRules("-keep class " + clazz.getTypeName());
    }
    return self();
  }

  public T addMainDexListClasses(Class<?>... classes) {
    builder.addMainDexClasses(
        Arrays.stream(classes).map(Class::getTypeName).collect(Collectors.toList()));
    return self();
  }

  public T allowUnusedProguardConfigurationRules() {
    return addOptionsModification(
        options -> options.testing.allowUnusedProguardConfigurationRules = true);
  }

  public T enableInliningAnnotations() {
    return enableInliningAnnotations(NeverInline.class.getPackage().getName());
  }

  public T enableInliningAnnotations(String annotationPackageName) {
    if (!enableInliningAnnotations) {
      enableInliningAnnotations = true;
      addInternalKeepRules(
          "-neverinline class * { @" + annotationPackageName + ".NeverInline *; }");
    }
    return self();
  }

  public T enableForceInliningAnnotations() {
    return enableForceInliningAnnotations(ForceInline.class.getPackage().getName());
  }

  public T enableForceInliningAnnotations(String annotationPackageName) {
    if (!enableInliningAnnotations) {
      enableInliningAnnotations = true;
      addInternalKeepRules(
          "-forceinline class * { @" + annotationPackageName + ".ForceInline *; }");
    }
    return self();
  }

  public T enableClassInliningAnnotations() {
    if (!enableClassInliningAnnotations) {
      enableClassInliningAnnotations = true;
      addInternalKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *");
    }
    return self();
  }

  public T enableMergeAnnotations() {
    if (!enableMergeAnnotations) {
      enableMergeAnnotations = true;
      addInternalKeepRules("-nevermerge @com.android.tools.r8.NeverMerge class *");
    }
    return self();
  }

  public T enableMemberValuePropagationAnnotations() {
    if (!enableMemberValuePropagationAnnotations) {
      enableMemberValuePropagationAnnotations = true;
      addInternalKeepRules(
          "-neverpropagatevalue class * { @com.android.tools.r8.NeverPropagateValue *; }");
    }
    return self();
  }

  public T enableSideEffectAnnotations() {
    if (!enableSideEffectAnnotations) {
      enableSideEffectAnnotations = true;
      addInternalKeepRules(
          "-assumemayhavesideeffects class * {",
          "  @com.android.tools.r8.AssumeMayHaveSideEffects <methods>;",
          "}");
    }
    return self();
  }

  public T assumeAllMethodsMayHaveSideEffects() {
    if (!enableSideEffectAnnotations) {
      enableSideEffectAnnotations = true;
      addInternalKeepRules("-assumemayhavesideeffects class * { <methods>; }");
    }
    return self();
  }

  public T enableConstantArgumentAnnotations() {
    return enableConstantArgumentAnnotations(true);
  }

  public T enableConstantArgumentAnnotations(boolean value) {
    if (value) {
      if (!enableConstantArgumentAnnotations) {
        enableConstantArgumentAnnotations = true;
        addInternalKeepRules(
            "-keepconstantarguments class * { @com.android.tools.r8.KeepConstantArguments *; }");
      }
    } else {
      assert !enableConstantArgumentAnnotations;
    }
    return self();
  }

  public T enableUnusedArgumentAnnotations() {
    return enableUnusedArgumentAnnotations(true);
  }

  public T enableUnusedArgumentAnnotations(boolean value) {
    if (value) {
      if (!enableUnusedArgumentAnnotations) {
        enableUnusedArgumentAnnotations = true;
        addInternalKeepRules(
            "-keepunusedarguments class * { @com.android.tools.r8.KeepUnusedArguments *; }");
      }
    } else {
      assert !enableUnusedArgumentAnnotations;
    }
    return self();
  }

  public T enableProguardTestOptions() {
    builder.allowTestProguardOptions();
    return self();
  }

  public T enableGraphInspector() {
    return enableGraphInspector(null);
  }

  public T enableGraphInspector(GraphConsumer subConsumer) {
    CollectingGraphConsumer consumer = new CollectingGraphConsumer(subConsumer);
    setKeptGraphConsumer(consumer);
    graphConsumer = consumer;
    return self();
  }

  public T setKeptGraphConsumer(GraphConsumer graphConsumer) {
    assert this.graphConsumer == null;
    builder.setKeptGraphConsumer(graphConsumer);
    return self();
  }

  public T setMainDexKeptGraphConsumer(GraphConsumer graphConsumer) {
    builder.setMainDexKeptGraphConsumer(graphConsumer);
    return self();
  }

  @Override
  public T addApplyMapping(String proguardMap) {
    applyMappingMaps.add(proguardMap);
    return self();
  }

  private void addInternalKeepRules(String... rules) {
    // We don't add these to the keep-rule set for other test provided rules.
    builder.addProguardConfiguration(Arrays.asList(rules), Origin.unknown());
  }

  @Override
  public T enableCoreLibraryDesugaring(
      AndroidApiLevel minAPILevel, KeepRuleConsumer keepRuleConsumer) {
    if (minAPILevel.getLevel() < AndroidApiLevel.O.getLevel()) {
      // Use P to mimic current Android Studio.
      builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P));
      builder.addSpecialLibraryConfiguration("default");
      builder.setDesugaredLibraryKeepRuleConsumer(keepRuleConsumer);
    }
    return self();
  }
}
