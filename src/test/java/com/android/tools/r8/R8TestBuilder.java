// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.dexsplitter.SplitterTestBase.simpleSplitProvider;
import static com.android.tools.r8.dexsplitter.SplitterTestBase.splitWithNonJavaFile;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;
import com.android.tools.r8.dexsplitter.SplitterTestBase.RunInterface;
import com.android.tools.r8.dexsplitter.SplitterTestBase.SplitRunner;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.NoHorizontalClassMergingRule;
import com.android.tools.r8.shaking.NoStaticClassMergingRule;
import com.android.tools.r8.shaking.NoUnusedInterfaceRemovalRule;
import com.android.tools.r8.shaking.NoVerticalClassMergingRule;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hamcrest.core.IsAnything;

public abstract class R8TestBuilder<T extends R8TestBuilder<T>>
    extends TestShrinkerBuilder<R8Command, Builder, R8TestCompileResult, R8TestRunResult, T> {

  enum AllowedDiagnosticMessages {
    ALL,
    ERROR,
    INFO,
    NONE,
    WARNING
  }

  R8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  private AllowedDiagnosticMessages allowedDiagnosticMessages = AllowedDiagnosticMessages.NONE;
  private boolean allowUnusedProguardConfigurationRules = false;
  private boolean enableAssumeNoSideEffectsAnnotations = false;
  private boolean enableConstantArgumentAnnotations = false;
  private boolean enableInliningAnnotations = false;
  private boolean enableMemberValuePropagationAnnotations = false;
  private boolean enableNoUnusedInterfaceRemovalAnnotations = false;
  private boolean enableNoVerticalClassMergingAnnotations = false;
  private boolean enableNoHorizontalClassMergingAnnotations = false;
  private boolean enableNoStaticClassMergingAnnotations = false;
  private boolean enableNeverClassInliningAnnotations = false;
  private boolean enableNeverReprocessClassInitializerAnnotations = false;
  private boolean enableNeverReprocessMethodAnnotations = false;
  private boolean enableReprocessClassInitializerAnnotations = false;
  private boolean enableReprocessMethodAnnotations = false;
  private boolean enableSideEffectAnnotations = false;
  private boolean enableUnusedArgumentAnnotations = false;
  private CollectingGraphConsumer graphConsumer = null;
  private List<String> keepRules = new ArrayList<>();
  private List<Path> mainDexRulesFiles = new ArrayList<>();
  private List<String> applyMappingMaps = new ArrayList<>();
  private final List<Path> features = new ArrayList<>();

  @Override
  R8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (enableConstantArgumentAnnotations
        || enableInliningAnnotations
        || enableMemberValuePropagationAnnotations
        || enableNoUnusedInterfaceRemovalAnnotations
        || enableNoVerticalClassMergingAnnotations
        || enableNoHorizontalClassMergingAnnotations
        || enableNoStaticClassMergingAnnotations
        || enableNeverClassInliningAnnotations
        || enableNeverReprocessClassInitializerAnnotations
        || enableNeverReprocessMethodAnnotations
        || enableReprocessClassInitializerAnnotations
        || enableReprocessMethodAnnotations
        || enableSideEffectAnnotations
        || enableUnusedArgumentAnnotations) {
      ToolHelper.allowTestProguardOptions(builder);
    }
    if (!keepRules.isEmpty()) {
      builder.addProguardConfiguration(keepRules, Origin.unknown());
    }
    builder.addMainDexRulesFiles(mainDexRulesFiles);
    StringBuilder proguardMapBuilder = new StringBuilder();
    builder.setDisableTreeShaking(!enableTreeShaking);
    builder.setDisableMinification(!enableMinification);
    builder.setProguardMapConsumer(
        new StringConsumer() {
          @Override
          public void accept(String string, DiagnosticsHandler handler) {
            proguardMapBuilder.append(string);
          }

          @Override
          public void finished(DiagnosticsHandler handler) {
            // Nothing to do.
          }
        });

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
    R8TestCompileResult compileResult =
        new R8TestCompileResult(
            getState(),
            getOutputMode(),
            app.get(),
            box.proguardConfiguration,
            box.syntheticProguardRules,
            proguardMapBuilder.toString(),
            graphConsumer,
            minApiLevel,
            features);
    switch (allowedDiagnosticMessages) {
      case ALL:
        compileResult.getDiagnosticMessages().assertAllDiagnosticsMatch(new IsAnything<>());
        break;
      case ERROR:
        compileResult.assertOnlyErrors();
        break;
      case INFO:
        compileResult.assertOnlyInfos();
        break;
      case NONE:
        if (allowUnusedProguardConfigurationRules) {
          compileResult
              .assertAllInfoMessagesMatch(
                  containsString("Proguard configuration rule does not match anything"))
              .assertNoErrorMessages()
              .assertNoWarningMessages();
        } else {
          compileResult.assertNoMessages();
        }
        break;
      case WARNING:
        compileResult.assertOnlyWarnings();
        break;
      default:
        throw new Unreachable();
    }
    if (allowUnusedProguardConfigurationRules) {
      compileResult.assertInfoMessageThatMatches(
          containsString("Proguard configuration rule does not match anything"));
    } else {
      compileResult.assertNoInfoMessageThatMatches(
          containsString("Proguard configuration rule does not match anything"));
    }
    return compileResult;
  }

  public Builder getBuilder() {
    return builder;
  }

  public T addProgramResourceProviders(Collection<ProgramResourceProvider> providers) {
    for (ProgramResourceProvider provider : providers) {
      builder.addProgramResourceProvider(provider);
    }
    return self();
  }

  public T addProgramResourceProviders(ProgramResourceProvider... providers) {
    return addProgramResourceProviders(Arrays.asList(providers));
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

  @Override
  public T addMainDexListClasses(Class<?>... classes) {
    builder.addMainDexClasses(
        Arrays.stream(classes).map(Class::getTypeName).collect(Collectors.toList()));
    return self();
  }

  public T allowClassInlinerGracefulExit() {
    return addOptionsModification(options -> options.testing.allowClassInlinerGracefulExit = true);
  }

  /**
   * Allow info, warning, and error diagnostics.
   *
   * <p>This should only be used if a test has any of these diagnostic messages. Therefore, it is a
   * failure if no such diagnostics are reported.
   */
  public T allowDiagnosticMessages() {
    assert allowedDiagnosticMessages == AllowedDiagnosticMessages.NONE;
    allowedDiagnosticMessages = AllowedDiagnosticMessages.ALL;
    return self();
  }

  public T allowDiagnosticInfoMessages() {
    return allowDiagnosticInfoMessages(true);
  }

  /**
   * Allow info diagnostics if {@param condition} is true.
   *
   * <p>This should only be used if a test has at least one diagnostic info message. Therefore, it
   * is a failure if no such diagnostics are reported.
   */
  public T allowDiagnosticInfoMessages(boolean condition) {
    if (condition) {
      assert allowedDiagnosticMessages == AllowedDiagnosticMessages.NONE;
      allowedDiagnosticMessages = AllowedDiagnosticMessages.INFO;
    }
    return self();
  }

  public T allowDiagnosticWarningMessages() {
    return allowDiagnosticWarningMessages(true);
  }

  /**
   * Allow warning diagnostics if {@param condition} is true.
   *
   * <p>This should only be used if a test has at least one diagnostic warning message. Therefore,
   * it is a failure if no such diagnostics are reported.
   */
  public T allowDiagnosticWarningMessages(boolean condition) {
    if (condition) {
      assert allowedDiagnosticMessages == AllowedDiagnosticMessages.NONE;
      allowedDiagnosticMessages = AllowedDiagnosticMessages.WARNING;
    }
    return self();
  }

  public T allowDiagnosticErrorMessages() {
    return allowDiagnosticErrorMessages(true);
  }

  /**
   * Allow error diagnostics if {@param condition} is true.
   *
   * <p>This should only be used if a test has at least one diagnostic error message. Therefore, it
   * is a failure if no such diagnostics are reported.
   */
  public T allowDiagnosticErrorMessages(boolean condition) {
    if (condition) {
      assert allowedDiagnosticMessages == AllowedDiagnosticMessages.NONE;
      allowedDiagnosticMessages = AllowedDiagnosticMessages.ERROR;
    }
    return self();
  }

  public T allowUnusedProguardConfigurationRules() {
    return allowUnusedProguardConfigurationRules(true);
  }

  public T allowUnusedProguardConfigurationRules(boolean condition) {
    if (condition) {
      allowUnusedProguardConfigurationRules = true;
    }
    return self();
  }

  public T enableAlwaysInliningAnnotations() {
    return enableAlwaysInliningAnnotations(AlwaysInline.class.getPackage().getName());
  }

  public T enableAlwaysInliningAnnotations(String annotationPackageName) {
    if (!enableInliningAnnotations) {
      enableInliningAnnotations = true;
      addInternalKeepRules(
          "-alwaysinline class * { @" + annotationPackageName + ".AlwaysInline *; }");
    }
    return self();
  }

  public T enableAssumeNoSideEffectsAnnotations() {
    return enableAssumeNoSideEffectsAnnotations(AssumeNoSideEffects.class.getPackage().getName());
  }

  public T enableAssumeNoSideEffectsAnnotations(String annotationPackageName) {
    if (!enableAssumeNoSideEffectsAnnotations) {
      enableAssumeNoSideEffectsAnnotations = true;
      addInternalKeepRules(
          "-assumenosideeffects class * { @"
              + annotationPackageName
              + ".AssumeNoSideEffects <methods>; }");
    }
    return self();
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

  public T enableNeverClassInliningAnnotations() {
    if (!enableNeverClassInliningAnnotations) {
      enableNeverClassInliningAnnotations = true;
      addInternalKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *");
    }
    return self();
  }

  private void addInternalMatchInterfaceRule(String name, Class matchInterface) {
    StringBuilder sb = new StringBuilder();
    sb.append("-");
    sb.append(name);
    sb.append(" @");
    sb.append(matchInterface.getTypeName());
    sb.append(" class *");
    addInternalKeepRules(sb.toString());
  }

  public T enableNoUnusedInterfaceRemovalAnnotations() {
    if (!enableNoUnusedInterfaceRemovalAnnotations) {
      enableNoUnusedInterfaceRemovalAnnotations = true;
      addInternalMatchInterfaceRule(
          NoUnusedInterfaceRemovalRule.RULE_NAME, NoUnusedInterfaceRemoval.class);
    }
    return self();
  }

  public T enableNoVerticalClassMergingAnnotations() {
    if (!enableNoVerticalClassMergingAnnotations) {
      enableNoVerticalClassMergingAnnotations = true;
      addInternalMatchInterfaceRule(
          NoVerticalClassMergingRule.RULE_NAME, NoVerticalClassMerging.class);
    }
    return self();
  }

  public T enableNoHorizontalClassMergingAnnotations() {
    if (!enableNoHorizontalClassMergingAnnotations) {
      enableNoHorizontalClassMergingAnnotations = true;
      addInternalMatchInterfaceRule(
          NoHorizontalClassMergingRule.RULE_NAME, NoHorizontalClassMerging.class);
    }
    return self();
  }

  public T enableNoStaticClassMergingAnnotations() {
    if (!enableNoStaticClassMergingAnnotations) {
      enableNoStaticClassMergingAnnotations = true;
      addInternalMatchInterfaceRule(NoStaticClassMergingRule.RULE_NAME, NoStaticClassMerging.class);
    }
    return self();
  }

  public T enableMemberValuePropagationAnnotations() {
    return enableMemberValuePropagationAnnotations(true);
  }

  public T enableMemberValuePropagationAnnotations(boolean enable) {
    if (enable) {
      if (!enableMemberValuePropagationAnnotations) {
        enableMemberValuePropagationAnnotations = true;
        addInternalKeepRules(
            "-neverpropagatevalue class * { @com.android.tools.r8.NeverPropagateValue *; }");
      }
    } else {
      assert !enableMemberValuePropagationAnnotations;
    }
    return self();
  }

  public T enableReprocessClassInitializerAnnotations() {
    if (!enableReprocessClassInitializerAnnotations) {
      enableReprocessClassInitializerAnnotations = true;
      addInternalKeepRules(
          "-reprocessclassinitializer @com.android.tools.r8.ReprocessClassInitializer class *");
    }
    return self();
  }

  public T enableNeverReprocessClassInitializerAnnotations() {
    if (!enableNeverReprocessClassInitializerAnnotations) {
      enableNeverReprocessClassInitializerAnnotations = true;
      addInternalKeepRules(
          "-neverreprocessclassinitializer @com.android.tools.r8.NeverReprocessClassInitializer"
              + " class *");
    }
    return self();
  }

  public T enableReprocessMethodAnnotations() {
    if (!enableReprocessMethodAnnotations) {
      enableReprocessMethodAnnotations = true;
      addInternalKeepRules(
          "-reprocessmethod class * {", "  @com.android.tools.r8.ReprocessMethod <methods>;", "}");
    }
    return self();
  }

  public T enableNeverReprocessMethodAnnotations() {
    if (!enableNeverReprocessMethodAnnotations) {
      enableNeverReprocessMethodAnnotations = true;
      addInternalKeepRules(
          "-neverreprocessmethod class * {",
          "  @com.android.tools.r8.NeverReprocessMethod <methods>;",
          "}");
    }
    return self();
  }

  public T enableProtoShrinking() {
    return enableProtoShrinking(true);
  }

  public T enableProtoShrinking(boolean traverseOneOfAndRepeatedProtoFields) {
    if (traverseOneOfAndRepeatedProtoFields) {
      addOptionsModification(
          options -> options.protoShrinking().traverseOneOfAndRepeatedProtoFields = true);
    }
    return addKeepRules("-shrinkunusedprotofields");
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
      AndroidApiLevel minApiLevel, KeepRuleConsumer keepRuleConsumer) {
    if (minApiLevel.getLevel() < AndroidApiLevel.O.getLevel()) {
      super.enableCoreLibraryDesugaring(minApiLevel, keepRuleConsumer);
      builder.setDesugaredLibraryKeepRuleConsumer(keepRuleConsumer);
    }
    return self();
  }

  public T addFeatureSplitRuntime() {
    addProgramClasses(SplitRunner.class, RunInterface.class);
    addKeepClassAndMembersRules(SplitRunner.class, RunInterface.class);
    return self();
  }

  public T addFeatureSplit(Function<FeatureSplit.Builder, FeatureSplit> featureSplitBuilder) {
    builder.addFeatureSplit(featureSplitBuilder);
    return self();
  }

  public T addFeatureSplit(Class<?>... classes) throws IOException {
    Path path = getState().getNewTempFile("feature.zip");
    builder.addFeatureSplit(
        builder -> simpleSplitProvider(builder, path, getState().getTempFolder(), classes));
    features.add(path);
    return self();
  }

  public T addFeatureSplitWithResources(
      Collection<Pair<String, String>> nonJavaFiles, Class<?>... classes) throws IOException {
    Path path = getState().getNewTempFolder().resolve("feature.zip");
    builder.addFeatureSplit(
        builder ->
            splitWithNonJavaFile(builder, path, getState().getTempFolder(), nonJavaFiles, classes));
    features.add(path);
    return self();
  }
}
