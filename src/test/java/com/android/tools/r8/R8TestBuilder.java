// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.dexsplitter.SplitterTestBase.simpleSplitProvider;
import static com.android.tools.r8.dexsplitter.SplitterTestBase.splitWithNonJavaFile;
import static com.android.tools.r8.utils.codeinspector.Matchers.proguardConfigurationRuleDoesNotMatch;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.dexsplitter.SplitterTestBase.RunInterface;
import com.android.tools.r8.dexsplitter.SplitterTestBase.SplitRunner;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.keepanno.KeepEdgeAnnotationsTest;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.shaking.CheckEnumUnboxedRule;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.KeepUnusedReturnValueRule;
import com.android.tools.r8.shaking.NoFieldTypeStrengtheningRule;
import com.android.tools.r8.shaking.NoHorizontalClassMergingRule;
import com.android.tools.r8.shaking.NoMethodStaticizingRule;
import com.android.tools.r8.shaking.NoParameterReorderingRule;
import com.android.tools.r8.shaking.NoParameterTypeStrengtheningRule;
import com.android.tools.r8.shaking.NoRedundantFieldLoadEliminationRule;
import com.android.tools.r8.shaking.NoReturnTypeStrengtheningRule;
import com.android.tools.r8.shaking.NoUnusedInterfaceRemovalRule;
import com.android.tools.r8.shaking.NoVerticalClassMergingRule;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MapIdTemplateProvider;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.SourceFileTemplateProvider;
import java.io.IOException;
import java.lang.annotation.Annotation;
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
  private boolean enableMissingLibraryApiModeling = true;
  private CollectingGraphConsumer graphConsumer = null;
  private final List<ExternalArtProfile> residualArtProfiles = new ArrayList<>();
  private final List<String> keepRules = new ArrayList<>();
  private final List<Path> mainDexRulesFiles = new ArrayList<>();
  private final List<String> applyMappingMaps = new ArrayList<>();
  private final List<Path> features = new ArrayList<>();
  private PartitionMapConsumer partitionMapConsumer = null;

  @Override
  public boolean isR8TestBuilder() {
    return true;
  }

  @Override
  public R8TestBuilder<?> asR8TestBuilder() {
    return this;
  }

  @Override
  R8TestCompileResult internalCompile(
      Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException {
    if (!keepRules.isEmpty()) {
      builder.addProguardConfiguration(keepRules, Origin.unknown());
    }
    builder.addMainDexRulesFiles(mainDexRulesFiles);
    if (enableTreeShaking.isFalse()) {
      builder.setDisableTreeShaking(true);
    }
    if (enableMinification.isFalse()) {
      builder.setDisableMinification(true);
    }
    StringBuilder proguardMapBuilder = new StringBuilder();
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
    builder.setPartitionMapConsumer(partitionMapConsumer);

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
    libraryDesugaringTestConfiguration.configure(builder);
    builder.setEnableExperimentalMissingLibraryApiModeling(enableMissingLibraryApiModeling);
    ToolHelper.runAndBenchmarkR8WithoutResult(
        builder,
        optionsConsumer.andThen(
            options -> box.proguardConfiguration = options.getProguardConfiguration()),
        benchmarkResults);
    R8TestCompileResult compileResult =
        new R8TestCompileResult(
            getState(),
            getOutputMode(),
            libraryDesugaringTestConfiguration,
            app.get(),
            box.proguardConfiguration,
            box.syntheticProguardRules,
            proguardMapBuilder.toString(),
            graphConsumer,
            getMinApiLevel(),
            features,
            residualArtProfiles);
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
              .assertAllInfosMatch(proguardConfigurationRuleDoesNotMatch())
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
      compileResult.assertInfoThatMatches(proguardConfigurationRuleDoesNotMatch());
    } else {
      compileResult.assertNoInfoThatMatches(proguardConfigurationRuleDoesNotMatch());
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

  public T addMainDexKeepClassRules(Class<?>... classes) {
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

  public T allowUnnecessaryDontWarnWildcards() {
    return addOptionsModification(
        options -> options.testing.allowUnnecessaryDontWarnWildcards = true);
  }

  public T allowUnusedDontWarnKotlinReflectJvmInternal() {
    addOptionsModification(
        options ->
            options.testing.allowedUnusedDontWarnPatterns.add("kotlin.reflect.jvm.internal.**"));
    return self();
  }

  public T allowUnusedDontWarnKotlinReflectJvmInternal(boolean condition) {
    if (condition) {
      allowUnusedDontWarnKotlinReflectJvmInternal();
    }
    return self();
  }

  public T allowUnusedDontWarnJavaLangClassValue() {
    addOptionsModification(
        options -> options.testing.allowedUnusedDontWarnPatterns.add("java.lang.ClassValue"));
    return self();
  }

  public T allowUnusedDontWarnJavaLangClassValue(boolean condition) {
    if (condition) {
      allowUnusedDontWarnJavaLangClassValue();
    }
    return self();
  }

  public T allowUnusedDontWarnPatterns() {
    return addOptionsModification(options -> options.testing.allowUnusedDontWarnRules = true);
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

  public T enableAlwaysClassInlineAnnotations() {
    return addAlwaysClassInlineAnnotation()
        .enableAlwaysClassInlineAnnotations(AlwaysClassInline.class.getPackage().getName());
  }

  public T enableAlwaysClassInlineAnnotations(String annotationPackageName) {
    return addInternalKeepRules(
        "-alwaysclassinline @" + annotationPackageName + ".AlwaysClassInline class *");
  }

  public T enableAlwaysInliningAnnotations() {
    return addAlwaysInliningAnnotations()
        .enableAlwaysInliningAnnotations(AlwaysInline.class.getPackage().getName());
  }

  public T enableAlwaysInliningAnnotations(String annotationPackageName) {
    return addInternalKeepRules(
        "-alwaysinline class * { @" + annotationPackageName + ".AlwaysInline *; }");
  }

  public T enableAssumeNotNullAnnotations() {
    return addAssumeNotNullAnnotation()
        .enableAssumeNotNullAnnotations(AssumeNotNull.class.getPackage().getName());
  }

  public T enableAssumeNotNullAnnotations(String annotationPackageName) {
    return addInternalKeepRules(
        "-assumevalues class * {",
        "  @" + annotationPackageName + ".AssumeNotNull *** * return 1;",
        "  @" + annotationPackageName + ".AssumeNotNull *** *(...) return 1;",
        "}");
  }

  public T enableAssumeNoClassInitializationSideEffectsAnnotations() {
    return addAssumeNoClassInitializationSideEffectsAnnotation()
        .enableAssumeNoClassInitializationSideEffectsAnnotations(
            AssumeNoClassInitializationSideEffects.class.getPackage().getName());
  }

  public T enableAssumeNoClassInitializationSideEffectsAnnotations(String annotationPackageName) {
    return addInternalKeepRules(
        "-assumenosideeffects @"
            + annotationPackageName
            + ".AssumeNoClassInitializationSideEffects class * {",
        "  void <clinit>();",
        "}");
  }

  public T enableAssumeNoSideEffectsAnnotations() {
    return addAssumeNoSideEffectsAnnotations()
        .enableAssumeNoSideEffectsAnnotations(AssumeNoSideEffects.class.getPackage().getName());
  }

  public T enableAssumeNoSideEffectsAnnotations(String annotationPackageName) {
    return addInternalKeepRules(
        "-assumenosideeffects class * {",
        "  @" + annotationPackageName + ".AssumeNoSideEffects <methods>;",
        "}");
  }

  public T enableInliningAnnotations() {
    return addInliningAnnotations()
        .enableInliningAnnotations(NeverInline.class.getPackage().getName());
  }

  public T enableInliningAnnotations(String annotationPackageName) {
    return addInternalKeepRules(
        "-neverinline class * { @" + annotationPackageName + ".NeverInline *; }");
  }

  public T enableNeverSingleCallerInlineAnnotations() {
    return addNeverSingleCallerInlineAnnotations()
        .addInternalKeepRules(
            "-neversinglecallerinline class * {",
            "  @com.android.tools.r8.NeverSingleCallerInline <methods>;",
            "}");
  }

  public T enableNeverClassInliningAnnotations() {
    return addNeverClassInliningAnnotations()
        .addInternalKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *");
  }

  T addInternalMatchAnnotationOnFieldRule(String name, Class<? extends Annotation> annotation) {
    return addInternalKeepRules(
        "-" + name + " class * { @" + annotation.getTypeName() + " <fields>; }");
  }

  T addInternalMatchAnnotationOnMethodRule(String name, Class<? extends Annotation> annotation) {
    return addInternalKeepRules(
        "-" + name + " class * { @" + annotation.getTypeName() + " <methods>; }");
  }

  T addInternalMatchInterfaceRule(String name, Class<?> matchInterface) {
    return addInternalKeepRules("-" + name + " @" + matchInterface.getTypeName() + " class *");
  }

  public T noClassInlining() {
    return noClassInlining(true);
  }

  public T noClassInlining(boolean condition) {
    if (condition) {
      return addOptionsModification(options -> options.enableClassInlining = false);
    }
    return self();
  }

  public T noClassInliningOfSynthetics() {
    return addOptionsModification(
        options -> options.testing.allowClassInliningOfSynthetics = false);
  }

  public T noHorizontalClassMerging() {
    return noHorizontalClassMerging(true);
  }

  public T noHorizontalClassMerging(boolean condition) {
    if (condition) {
      return addKeepRules("-" + NoHorizontalClassMergingRule.RULE_NAME + " class *");
    }
    return self();
  }

  public T noHorizontalClassMerging(Class<?> clazz) {
    return noHorizontalClassMerging(clazz.getTypeName());
  }

  public T noHorizontalClassMerging(String typeName) {
    return addKeepRules("-" + NoHorizontalClassMergingRule.RULE_NAME + " class " + typeName)
        .enableProguardTestOptions();
  }

  public T noHorizontalClassMergingOfSynthetics() {
    return addOptionsModification(
        options -> options.horizontalClassMergerOptions().disableSyntheticMerging());
  }

  public T noInliningOfSynthetics() {
    return addOptionsModification(options -> options.testing.allowInliningOfSynthetics = false);
  }

  public T enableCheckEnumUnboxedAnnotations() {
    return addCheckEnumUnboxedAnnotation()
        .addInternalMatchInterfaceRule(CheckEnumUnboxedRule.RULE_NAME, CheckEnumUnboxed.class)
        .enableExperimentalCheckEnumUnboxed();
  }

  public T enableKeepUnusedReturnValueAnnotations() {
    return addKeepUnusedReturnValueAnnotation()
        .addInternalMatchAnnotationOnMethodRule(
            KeepUnusedReturnValueRule.RULE_NAME, KeepUnusedReturnValue.class);
  }

  public T enableNoFieldTypeStrengtheningAnnotations() {
    return addNoFieldTypeStrengtheningAnnotation()
        .addInternalMatchAnnotationOnFieldRule(
            NoFieldTypeStrengtheningRule.RULE_NAME, NoFieldTypeStrengthening.class);
  }

  public T enableNoMethodStaticizingAnnotations() {
    return addNoMethodStaticizingAnnotation()
        .addInternalMatchAnnotationOnMethodRule(
            NoMethodStaticizingRule.RULE_NAME, NoMethodStaticizing.class);
  }

  public T enableNoParameterReorderingAnnotations() {
    return addNoParameterReorderingAnnotation()
        .addInternalMatchAnnotationOnMethodRule(
            NoParameterReorderingRule.RULE_NAME, NoParameterReordering.class);
  }

  public T enableNoParameterTypeStrengtheningAnnotations() {
    return addNoParameterTypeStrengtheningAnnotation()
        .addInternalMatchAnnotationOnMethodRule(
            NoParameterTypeStrengtheningRule.RULE_NAME, NoParameterTypeStrengthening.class);
  }

  public T enableNoRedundantFieldLoadEliminationAnnotations() {
    return addNoRedundantFieldLoadEliminationAnnotation()
        .addInternalMatchAnnotationOnFieldRule(
            NoRedundantFieldLoadEliminationRule.RULE_NAME, NoRedundantFieldLoadElimination.class);
  }

  public T enableNoReturnTypeStrengtheningAnnotations() {
    return addNoReturnTypeStrengtheningAnnotation()
        .addInternalMatchAnnotationOnMethodRule(
            NoReturnTypeStrengtheningRule.RULE_NAME, NoReturnTypeStrengthening.class);
  }

  public T enableNoUnusedInterfaceRemovalAnnotations() {
    return addNoUnusedInterfaceRemovalAnnotations()
        .addInternalMatchInterfaceRule(
            NoUnusedInterfaceRemovalRule.RULE_NAME, NoUnusedInterfaceRemoval.class);
  }

  public T enableNoVerticalClassMergingAnnotations() {
    return addNoVerticalClassMergingAnnotations()
        .addInternalMatchInterfaceRule(
            NoVerticalClassMergingRule.RULE_NAME, NoVerticalClassMerging.class);
  }

  public T enableNoHorizontalClassMergingAnnotations() {
    return addNoHorizontalClassMergingAnnotations()
        .addInternalMatchInterfaceRule(
            NoHorizontalClassMergingRule.RULE_NAME, NoHorizontalClassMerging.class);
  }

  public T addNoHorizontalClassMergingRule(String clazz) {
    return addInternalKeepRules("-nohorizontalclassmerging class " + clazz);
  }

  public T addNoHorizontalClassMergingRule(String... classes) {
    for (String clazz : classes) {
      addNoHorizontalClassMergingRule(clazz);
    }
    return self();
  }

  public T enableMemberValuePropagationAnnotations() {
    return enableMemberValuePropagationAnnotations(true);
  }

  public T enableMemberValuePropagationAnnotations(boolean enable) {
    if (enable) {
      return addMemberValuePropagationAnnotations()
          .addInternalKeepRules(
              "-neverpropagatevalue class * { @com.android.tools.r8.NeverPropagateValue *; }");
    }
    return self();
  }

  public T enableReprocessClassInitializerAnnotations() {
    return addReprocessClassInitializerAnnotations()
        .addInternalKeepRules(
            "-reprocessclassinitializer @com.android.tools.r8.ReprocessClassInitializer class *");
  }

  public T enableNeverReprocessClassInitializerAnnotations() {
    return addNeverReprocessClassInitializerAnnotations()
        .addInternalKeepRules(
            "-neverreprocessclassinitializer @com.android.tools.r8.NeverReprocessClassInitializer"
                + " class *");
  }

  public T enableReprocessMethodAnnotations() {
    return addReprocessMethodAnnotations()
        .addInternalKeepRules(
            "-reprocessmethod class * {",
            "  @com.android.tools.r8.ReprocessMethod <methods>;",
            "}");
  }

  public T enableNeverReprocessMethodAnnotations() {
    return addNeverReprocessMethodAnnotations()
        .addInternalKeepRules(
            "-neverreprocessmethod class * {",
            "  @com.android.tools.r8.NeverReprocessMethod <methods>;",
            "}");
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
    return addSideEffectAnnotations()
        .addInternalKeepRules(
            "-assumemayhavesideeffects class * {",
            "  @com.android.tools.r8.AssumeMayHaveSideEffects <methods>;",
            "}");
  }

  public T assumeAllMethodsMayHaveSideEffects() {
    return addInternalKeepRules("-assumemayhavesideeffects class * { <methods>; }");
  }

  public T enableConstantArgumentAnnotations() {
    return enableConstantArgumentAnnotations(true);
  }

  public T enableConstantArgumentAnnotations(boolean value) {
    if (value) {
      return addConstantArgumentAnnotations()
          .addInternalKeepRules(
              "-keepconstantarguments class * { @com.android.tools.r8.KeepConstantArguments *; }");
    }
    return self();
  }

  public T enableUnusedArgumentAnnotations() {
    return enableUnusedArgumentAnnotations(true);
  }

  public T enableUnusedArgumentAnnotations(boolean value) {
    if (value) {
      return addUnusedArgumentAnnotations()
          .addInternalKeepRules(
              "-keepunusedarguments class * { @com.android.tools.r8.KeepUnusedArguments *; }");
    }
    return self();
  }

  public T enableExperimentalCheckEnumUnboxed() {
    builder.setEnableExperimentalCheckEnumUnboxed();
    return self();
  }

  public T enableExperimentalConvertCheckNotNull() {
    builder.setEnableExperimentalConvertCheckNotNull();
    return self();
  }

  public T enableExperimentalWhyAreYouNotInlining() {
    builder.setEnableExperimentalWhyAreYouNotInlining();
    return self();
  }

  public T enableExperimentalKeepAnnotations() {
    builder.addClasspathResourceProvider(
        DirectoryClassFileProvider.fromDirectory(KeepEdgeAnnotationsTest.KEEP_ANNO_PATH));
    builder.setEnableExperimentalKeepAnnotations(true);
    return self();
  }

  public T enableProguardTestOptions() {
    builder.setEnableTestProguardOptions();
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

  public T setMapIdTemplate(String mapIdTemplate) {
    builder.setMapIdProvider(MapIdTemplateProvider.create(mapIdTemplate, builder.getReporter()));
    return self();
  }

  public T setSourceFileTemplate(String sourceFileTemplate) {
    builder.setSourceFileProvider(
        SourceFileTemplateProvider.create(sourceFileTemplate, builder.getReporter()));
    return self();
  }

  @Override
  public T addApplyMapping(String proguardMap) {
    applyMappingMaps.add(proguardMap);
    return self();
  }

  T addInternalKeepRules(String... rules) {
    // We don't add these to the keep-rule set for other test provided rules.
    builder.addProguardConfiguration(Arrays.asList(rules), Origin.unknown());
    return enableProguardTestOptions();
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

  public T addArtProfileForRewriting(ArtProfileProvider artProfileProvider) {
    return addArtProfileForRewriting(
        artProfileProvider,
        ArtProfileTestingUtils.createResidualArtProfileConsumer(residualArtProfiles::add));
  }

  public T addArtProfileForRewriting(ExternalArtProfile artProfile) {
    return addArtProfileForRewriting(ArtProfileTestingUtils.createArtProfileProvider(artProfile));
  }

  public T addArtProfileForRewriting(
      ArtProfileProvider artProfileProvider, ArtProfileConsumer residualArtProfileConsumer) {
    builder.addArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer);
    return self();
  }

  public T addStartupProfileProviders(StartupProfileProvider... startupProfileProviders) {
    builder.addStartupProfileProviders(startupProfileProviders);
    return self();
  }

  public T addStartupProfileProviders(Collection<StartupProfileProvider> startupProfileProviders) {
    builder.addStartupProfileProviders(startupProfileProviders);
    return self();
  }

  public T setFakeCompilerVersion(SemanticVersion version) {
    getBuilder().setFakeCompilerVersion(version);
    return self();
  }

  public T setPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
    this.partitionMapConsumer = partitionMapConsumer;
    return self();
  }
}
