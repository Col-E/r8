// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.InternalOptions.DETERMINISTIC_DEBUGGING;
import static com.android.tools.r8.utils.MapConsumerUtils.wrapExistingMapConsumerIfNotNull;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.naming.ProguardMapStringConsumer;
import com.android.tools.r8.naming.SourceFileRewriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserOptions;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceBytes;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.InternalOptions.MappingComposeOptions;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Immutable command structure for an invocation of the {@link R8} compiler.
 *
 * <p>To build a R8 command use the {@link R8Command.Builder} class. For example:
 *
 * <pre>
 *   R8Command command = R8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .setOutput(Paths.get("output.zip", OutputMode.DexIndexed))
 *     .build();
 * </pre>
 */
@KeepForApi
public final class R8Command extends BaseCompilerCommand {

  /**
   * Builder for constructing a R8Command.
   *
   * <p>A builder is obtained by calling {@link R8Command#builder}.
   */
  @KeepForApi
  public static class Builder extends BaseCompilerCommand.Builder<R8Command, Builder> {

    private static class DefaultR8DiagnosticsHandler implements DiagnosticsHandler {

      @Override
      public void error(Diagnostic error) {
        if (error instanceof DexFileOverflowDiagnostic) {
          DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
          if (!overflowDiagnostic.hasMainDexSpecification()) {
            DiagnosticsHandler.super.error(
                new StringDiagnostic(
                    overflowDiagnostic.getDiagnosticMessage()
                        + ". Try supplying a main-dex list or main-dex rules"));
            return;
          }
        }
        DiagnosticsHandler.super.error(error);
      }
    }

    private final List<ProguardConfigurationSource> mainDexRules = new ArrayList<>();
    private Consumer<ProguardConfiguration.Builder> proguardConfigurationConsumerForTesting = null;
    private Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer = null;
    private StringConsumer desugaredLibraryKeepRuleConsumer = null;
    private final List<ProguardConfigurationSource> proguardConfigs = new ArrayList<>();
    private boolean disableTreeShaking = false;
    private boolean disableMinification = false;
    private boolean disableVerticalClassMerging = false;
    private boolean forceProguardCompatibility = false;
    private Optional<Boolean> includeDataResources = Optional.empty();
    private StringConsumer proguardUsageConsumer = null;
    private StringConsumer proguardSeedsConsumer = null;
    private StringConsumer proguardConfigurationConsumer = null;
    private GraphConsumer keptGraphConsumer = null;
    private GraphConsumer mainDexKeptGraphConsumer = null;
    private InputDependencyGraphConsumer inputDependencyGraphConsumer = null;
    private final List<FeatureSplit> featureSplits = new ArrayList<>();
    private String synthesizedClassPrefix = "";
    private boolean enableMissingLibraryApiModeling = false;
    private boolean enableExperimentalKeepAnnotations =
        System.getProperty("com.android.tools.r8.enableKeepAnnotations") != null;
    private SemanticVersion fakeCompilerVersion = null;
    private AndroidResourceProvider androidResourceProvider = null;
    private AndroidResourceConsumer androidResourceConsumer = null;
    private ResourceShrinkerConfiguration resourceShrinkerConfiguration =
        ResourceShrinkerConfiguration.DEFAULT_CONFIGURATION;

    private final ProguardConfigurationParserOptions.Builder parserOptionsBuilder =
        ProguardConfigurationParserOptions.builder().readEnvironment();
    private final boolean allowDexInArchive =
        System.getProperty("com.android.tools.r8.allowDexInputToR8") != null;

    // TODO(zerny): Consider refactoring CompatProguardCommandBuilder to avoid subclassing.
    Builder() {
      this(new DefaultR8DiagnosticsHandler());
    }

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
      setIgnoreDexInArchive(!allowDexInArchive);
    }

    private Builder(AndroidApp app) {
      super(app);
      setIgnoreDexInArchive(!allowDexInArchive);
    }

    private Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(app, diagnosticsHandler);
      setIgnoreDexInArchive(!allowDexInArchive);
    }

    // Internal

    void setDisableVerticalClassMerging(boolean disableVerticalClassMerging) {
      this.disableVerticalClassMerging = disableVerticalClassMerging;
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.RELEASE;
    }

    Builder setSynthesizedClassesPrefix(String prefix) {
      synthesizedClassPrefix = prefix;
      return self();
    }

    Builder setFakeCompilerVersion(SemanticVersion version) {
      fakeCompilerVersion = version;
      return self();
    }

    /**
     * Disable tree shaking.
     *
     * <p>If true, tree shaking is completely disabled, otherwise tree shaking is configured by
     * ProGuard configuration settings.
     */
    public Builder setDisableTreeShaking(boolean disableTreeShaking) {
      this.disableTreeShaking = disableTreeShaking;
      return self();
    }

    /**
     * Disable minification of names.
     *
     * <p>If true, minification of names is completely disabled, otherwise minification of names is
     * configured by ProGuard configuration settings.
     */
    public Builder setDisableMinification(boolean disableMinification) {
      this.disableMinification = disableMinification;
      return self();
    }

    /** Add proguard configuration files with rules for automatic main-dex-list calculation. */
    public Builder addMainDexRulesFiles(Path... paths) {
      return addMainDexRulesFiles(Arrays.asList(paths));
    }

    /** Add proguard configuration files with rules for automatic main-dex-list calculation. */
    public Builder addMainDexRulesFiles(Collection<Path> paths) {
      guard(() -> paths.forEach(p -> mainDexRules.add(new ProguardConfigurationSourceFile(p))));
      return self();
    }

    /** Add proguard rules for automatic main-dex-list calculation. */
    public Builder addMainDexRules(List<String> lines, Origin origin) {
      guard(
          () ->
              mainDexRules.add(
                  new ProguardConfigurationSourceStrings(lines, Paths.get("."), origin)));
      return self();
    }

    /**
     * Set Proguard compatibility mode.
     *
     * <p>If true, R8 will attempt to retain more compatibility with Proguard. Most notably, R8 will
     * introduce rules for keeping more default constructors as well as various attributes. Note
     * that setting R8 in compatibility mode will result in larger residual programs.
     */
    public Builder setProguardCompatibility(boolean value) {
      this.forceProguardCompatibility = value;
      return self();
    }

    /** Get the current value of Proguard compatibility mode. */
    public boolean getProguardCompatibility() {
      return forceProguardCompatibility;
    }

    /** Add proguard configuration-file resources. */
    public Builder addProguardConfigurationFiles(Path... paths) {
      guard(() -> {
        for (Path path : paths) {
          proguardConfigs.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /** Add proguard configuration-file resources. */
    public Builder addProguardConfigurationFiles(List<Path> paths) {
      guard(() -> {
        for (Path path : paths) {
          proguardConfigs.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /** Add proguard configuration. */
    public Builder addProguardConfiguration(List<String> lines, Origin origin) {
      guard(() -> proguardConfigs.add(
          new ProguardConfigurationSourceStrings(lines, Paths.get("."), origin)));
      return self();
    }

    /**
     * Set an output destination to which proguard-map content should be written.
     *
     * <p>This is a short-hand for setting a {@link StringConsumer.FileConsumer} using {@link
     * #setProguardMapConsumer}. Note that any subsequent call to this method or {@link
     * #setProguardMapConsumer} will override the previous setting.
     *
     * @param proguardMapOutput File-system path to write output at.
     */
    @Override
    public Builder setProguardMapOutputPath(Path proguardMapOutput) {
      return super.setProguardMapOutputPath(proguardMapOutput);
    }

    /** Set input proguard map used for distribution of classes in multi-dex. */
    public Builder setProguardMapInputFile(Path proguardInputMap) {
      getAppBuilder().setProguardMapInputData(proguardInputMap);
      return self();
    }

    /**
     * Set a consumer for receiving the proguard-map content.
     *
     * <p>Note that any subsequent call to this method or {@link #setProguardMapOutputPath} will
     * override the previous setting.
     *
     * @param proguardMapConsumer Consumer to receive the content once produced.
     */
    @Override
    public Builder setProguardMapConsumer(StringConsumer proguardMapConsumer) {
      return super.setProguardMapConsumer(proguardMapConsumer);
    }

    /**
     * Set a consumer for receiving the keep rules to use when compiling the desugared library for
     * the program being compiled in this compilation.
     *
     * @param keepRuleConsumer Consumer to receive the content once produced.
     */
    public Builder setDesugaredLibraryKeepRuleConsumer(StringConsumer keepRuleConsumer) {
      this.desugaredLibraryKeepRuleConsumer = keepRuleConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving the proguard usage information.
     *
     * <p>Note that any subsequent calls to this method will replace the previous setting.
     *
     * @param proguardUsageConsumer Consumer to receive usage information.
     */
    public Builder setProguardUsageConsumer(StringConsumer proguardUsageConsumer) {
      this.proguardUsageConsumer = proguardUsageConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving the proguard seeds information.
     *
     * <p>Note that any subsequent calls to this method will replace the previous setting.
     *
     * @param proguardSeedsConsumer Consumer to receive seeds information.
     */
    public Builder setProguardSeedsConsumer(StringConsumer proguardSeedsConsumer) {
      this.proguardSeedsConsumer = proguardSeedsConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving the proguard configuration information.
     *
     * <p>Note that any subsequent calls to this method will replace the previous setting.
     * @param proguardConfigurationConsumer
     */
    public Builder setProguardConfigurationConsumer(StringConsumer proguardConfigurationConsumer) {
      this.proguardConfigurationConsumer = proguardConfigurationConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving kept-graph events.
     */
    public Builder setKeptGraphConsumer(GraphConsumer graphConsumer) {
      this.keptGraphConsumer = graphConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving kept-graph events for the content of the main-dex output.
     */
    public Builder setMainDexKeptGraphConsumer(GraphConsumer graphConsumer) {
      this.mainDexKeptGraphConsumer = graphConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving dependency edges for files referenced from inputs.
     *
     * <p>Note that these dependency edges are for files read when reading/parsing other input
     * files, such as the proguard configuration files. For compilation dependencies for incremental
     * desugaring see {@code setDesugarGraphConsumer}.
     */
    public Builder setInputDependencyGraphConsumer(
        InputDependencyGraphConsumer inputDependencyGraphConsumer) {
      this.inputDependencyGraphConsumer = inputDependencyGraphConsumer;
      return self();
    }

    /**
     * Set the output path-and-mode.
     *
     * <p>Setting the output path-and-mode will override any previous set consumer or any previous
     * output path-and-mode, and implicitly sets the appropriate program consumer to write the
     * output.
     *
     * <p>By default data resources from the input will be included in the output. (see {@link
     * #setOutput(Path, OutputMode, boolean) for details}
     *
     * @param outputPath Path to write the output to. Must be an archive or and existing directory.
     * @param outputMode Mode in which to write the output.
     */
    @Override
    public Builder setOutput(Path outputPath, OutputMode outputMode) {
      setOutput(outputPath, outputMode, true);
      return self();
    }

    /**
     * Set the output path-and-mode and control if data resources are included.
     *
     * <p>In addition to setting the output path-and-mode (see {@link #setOutput(Path, OutputMode)})
     * this can control if data resources should be included or not.
     *
     * <p>Data resources are non Java classfile items in the input.
     *
     * <p>If data resources are not included they are ignored in the input and will not produce
     * anything in the output. If data resources are included they are processed according to the
     * configuration and written to the output.
     *
     * @param outputPath Path to write the output to. Must be an archive or and existing directory.
     * @param outputMode Mode in which to write the output.
     * @param includeDataResources If data resources from the input should be included in the
     *     output.
     */
    @Override
    public Builder setOutput(Path outputPath, OutputMode outputMode, boolean includeDataResources) {
      this.includeDataResources = Optional.of(includeDataResources);
      return super.setOutput(outputPath, outputMode, includeDataResources);
    }

    @Override
    public Builder addProgramResourceProvider(ProgramResourceProvider programProvider) {
      return super.addProgramResourceProvider(
          new EnsureNonDexProgramResourceProvider(programProvider));
    }

    /**
     * Add a {@link FeatureSplit} to the app. The {@link FeatureSplit} contains input and output
     * providers, that enables us to generate dynamic apps with optional modules.
     *
     * @param featureSplitGenerator A function that uses the supplied {@link FeatureSplit.Builder}
     *     to generate a {@link FeatureSplit}.
     */
    public Builder addFeatureSplit(
        Function<FeatureSplit.Builder, FeatureSplit> featureSplitGenerator) {
      FeatureSplit featureSplit = featureSplitGenerator.apply(FeatureSplit.builder(getReporter()));
      featureSplits.add(featureSplit);
      for (ProgramResourceProvider programResourceProvider : featureSplit
          .getProgramResourceProviders()) {
        // Data resources are handled separately and passed directly to the feature split consumer.
        ProgramResourceProvider providerWithoutDataResources = new ProgramResourceProvider() {
          @Override
          public Collection<ProgramResource> getProgramResources() throws ResourceException {
            return programResourceProvider.getProgramResources();
          }

          @Override
          public DataResourceProvider getDataResourceProvider() {
            return null;
          }
        };
        addProgramResourceProvider(providerWithoutDataResources);
      }
      return self();
    }

    /**
     * Enable experimental/pre-release support for modeling missing library APIs.
     *
     * <p>This allows enabling the feature while it is still default disabled by the compiler. Once
     * the feature is default enabled, calling this method will have no affect.
     */
    @Deprecated
    public Builder setEnableExperimentalMissingLibraryApiModeling(boolean enable) {
      this.enableMissingLibraryApiModeling = enable;
      return self();
    }

    @Deprecated
    public Builder setEnableExperimentalKeepAnnotations(boolean enable) {
      this.enableExperimentalKeepAnnotations = enable;
      return self();
    }

    @Override
    protected InternalProgramOutputPathConsumer createProgramOutputConsumer(
        Path path,
        OutputMode mode,
        boolean consumeDataResources) {
      return super.createProgramOutputConsumer(path, mode, consumeDataResources);
    }

    /**
     * Add a collection of startup profile providers that should be used for distributing the
     * program classes in dex. The given startup profiles are also used to disallow optimizations
     * across the startup and post-startup boundary.
     *
     * <p>NOTE: Startup profiles are ignored when compiling to class files or the min-API level does
     * not support native multidex (API<=20).
     */
    @Override
    public Builder addStartupProfileProviders(StartupProfileProvider... startupProfileProviders) {
      return super.addStartupProfileProviders(startupProfileProviders);
    }

    /**
     * Add a collection of startup profile providers that should be used for distributing the
     * program classes in dex. The given startup profiles are also used to disallow optimizations
     * across the startup and post-startup boundary.
     *
     * <p>NOTE: Startup profiles are ignored when compiling to class files or the min-API level does
     * not support native multidex (API<=20).
     */
    @Override
    public Builder addStartupProfileProviders(
        Collection<StartupProfileProvider> startupProfileProviders) {
      return super.addStartupProfileProviders(startupProfileProviders);
    }

    /**
     * Exprimental API for supporting android resource shrinking.
     *
     * <p>Add an android resource provider, providing the resource table, manifest and res table
     * entries.
     */
    public Builder setAndroidResourceProvider(AndroidResourceProvider provider) {
      this.androidResourceProvider = provider;
      return this;
    }

    /**
     * Exprimental API for supporting android resource shrinking.
     *
     * <p>Add an android resource consumer, consuming the resource table, manifest and res table
     * entries.
     */
    public Builder setAndroidResourceConsumer(AndroidResourceConsumer consumer) {
      this.androidResourceConsumer = consumer;
      return this;
    }

    /**
     * API for configuring resource shrinking.
     *
     * <p>Set the configuration properties on the provided builder.
     */
    public Builder setResourceShrinkerConfiguration(
        Function<ResourceShrinkerConfiguration.Builder, ResourceShrinkerConfiguration>
            configurationBuilder) {
      this.resourceShrinkerConfiguration =
          configurationBuilder.apply(ResourceShrinkerConfiguration.builder(getReporter()));
      return this;
    }

    @Override
    void validate() {
      if (isPrintHelp()) {
        return;
      }
      Reporter reporter = getReporter();
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("R8 does not support compiling to a single DEX file per Java class file");
      }
      if (getMainDexListConsumer() != null
          && mainDexRules.isEmpty()
          && !getAppBuilder().hasMainDexList()) {
        reporter.error(
            "Option --main-dex-list-output requires --main-dex-rules and/or --main-dex-list");
      }
      if (!(getProgramConsumer() instanceof ClassFileConsumer)
          && getMinApiLevel() >= AndroidApiLevel.L.getLevel()) {
        if (getMainDexListConsumer() != null
            || !mainDexRules.isEmpty()
            || getAppBuilder().hasMainDexList()) {
          reporter.error(
              "R8 does not support main-dex inputs and outputs when compiling to API level "
                  + AndroidApiLevel.L.getLevel()
                  + " and above");
        }
      }
      for (FeatureSplit featureSplit : featureSplits) {
        verifyResourceSplitOrProgramSplit(featureSplit);
        if (getProgramConsumer() != null && !(getProgramConsumer() instanceof DexIndexedConsumer)) {
          reporter.error("R8 does not support class file output when using feature splits");
        }
      }

      for (Path file : programFiles) {
        if (FileUtils.isDexFile(file)) {
          reporter.error(new StringDiagnostic(
              "R8 does not support compiling DEX inputs", new PathOrigin(file)));
        }
      }
      if (getProgramConsumer() instanceof ClassFileConsumer && isMinApiLevelSet()) {
        reporter.error("R8 does not support --min-api when compiling to class files");
      }
      if (hasDesugaredLibraryConfiguration() && getDisableDesugaring()) {
        reporter.error("Using desugared library configuration requires desugaring to be enabled");
      }
      super.validate();
    }

    private static void verifyResourceSplitOrProgramSplit(FeatureSplit featureSplit) {
      assert featureSplit.getProgramConsumer() instanceof DexIndexedConsumer
          || featureSplit.getAndroidResourceProvider() != null;
    }

    @Override
    R8Command makeCommand() {
      // If printing versions ignore everything else.
      if (isPrintHelp() || isPrintVersion()) {
        return new R8Command(isPrintHelp(), isPrintVersion());
      }
      return makeR8Command();
    }

    private R8Command makeR8Command() {
      Reporter reporter = getReporter();
      DexItemFactory factory = new DexItemFactory();
      List<ProguardConfigurationRule> mainDexKeepRules =
          ProguardConfigurationParser.parse(mainDexRules, factory, reporter);

      DesugaredLibrarySpecification desugaredLibrarySpecification =
          getDesugaredLibraryConfiguration(factory, false);

      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(
              factory, reporter, parserOptionsBuilder.build(), inputDependencyGraphConsumer);
      if (!proguardConfigs.isEmpty()) {
        parser.parse(proguardConfigs);
      }
      ProguardConfiguration.Builder configurationBuilder = parser.getConfigurationBuilder();
      configurationBuilder.setForceProguardCompatibility(forceProguardCompatibility);

      if (getMode() == CompilationMode.DEBUG) {
        disableMinification = true;
        configurationBuilder.disableOptimization();
      }

      if (disableTreeShaking) {
        configurationBuilder.disableShrinking();
      }

      if (disableMinification) {
        configurationBuilder.disableObfuscation();
      }

      if (proguardConfigurationConsumerForTesting != null) {
        proguardConfigurationConsumerForTesting.accept(configurationBuilder);
      }

      // Add embedded keep rules.
      amendWithRulesAndProvidersForInjarsAndMetaInf(reporter, parser);

      // Extract out rules for keep annotations and amend the configuration.
      // TODO(b/248408342): Remove this and parse annotations as part of R8 root-set & enqueuer.
      extractKeepAnnotationRules(parser);
      ProguardConfiguration configuration = configurationBuilder.build();
      getAppBuilder().addFilteredLibraryArchives(configuration.getLibraryjars());

      assert getProgramConsumer() != null;

      DesugarState desugaring =
          (getProgramConsumer() instanceof ClassFileConsumer)
              ? DesugarState.OFF
              : getDesugaringState();

      FeatureSplitConfiguration featureSplitConfiguration =
          !featureSplits.isEmpty() ? new FeatureSplitConfiguration(featureSplits) : null;

      R8Command command =
          new R8Command(
              getAppBuilder().build(),
              getProgramConsumer(),
              mainDexKeepRules,
              getMainDexListConsumer(),
              configuration,
              getMode(),
              getMinApiLevel(),
              reporter,
              desugaring,
              configuration.isShrinking(),
              configuration.isObfuscating(),
              disableVerticalClassMerging,
              forceProguardCompatibility,
              includeDataResources,
              proguardMapConsumer,
              partitionMapConsumer,
              proguardUsageConsumer,
              proguardSeedsConsumer,
              proguardConfigurationConsumer,
              keptGraphConsumer,
              mainDexKeptGraphConsumer,
              syntheticProguardRulesConsumer,
              isOptimizeMultidexForLinearAlloc(),
              getIncludeClassesChecksum(),
              getDexClassChecksumFilter(),
              desugaredLibraryKeepRuleConsumer,
              desugaredLibrarySpecification,
              featureSplitConfiguration,
              getAssertionsConfiguration(),
              getOutputInspections(),
              synthesizedClassPrefix,
              getThreadCount(),
              getDumpInputFlags(),
              getMapIdProvider(),
              getSourceFileProvider(),
              enableMissingLibraryApiModeling,
              getAndroidPlatformBuild(),
              getArtProfilesForRewriting(),
              getStartupProfileProviders(),
              getClassConflictResolver(),
              getCancelCompilationChecker(),
              androidResourceProvider,
              androidResourceConsumer,
              resourceShrinkerConfiguration);

      if (inputDependencyGraphConsumer != null) {
        inputDependencyGraphConsumer.finished();
      }
      return command;
    }

    private void amendWithRulesAndProvidersForInjarsAndMetaInf(
        Reporter reporter, ProguardConfigurationParser parser) {

      // Since -injars can itself reference archives with rules and that in turn have -injars the
      // completion of amending rules and providers must run in a fixed point. The fixed point is
      // reached once the injars set is stable.
      Set<FilteredClassPath> seenInjars = SetUtils.newIdentityHashSet();
      Deque<ProgramResourceProvider> providers =
          new ArrayDeque<>(getAppBuilder().getProgramResourceProviders());
      while (true) {
        for (FilteredClassPath injar : parser.getConfigurationBuilder().getInjars()) {
          if (seenInjars.add(injar)) {
            ArchiveResourceProvider provider = getAppBuilder().createAndAddProvider(injar);
            if (provider != null) {
              providers.add(provider);
            }
          }
        }
        if (providers.isEmpty()) {
          return;
        }

        Supplier<SemanticVersion> semanticVersionSupplier =
            Suppliers.memoize(
                () -> {
                  SemanticVersion compilerVersion =
                      fakeCompilerVersion == null
                          ? SemanticVersion.create(
                              Version.getMajorVersion(),
                              Version.getMinorVersion(),
                              Version.getPatchVersion())
                          : fakeCompilerVersion;
                  if (compilerVersion.getMajor() < 0) {
                    compilerVersion =
                        SemanticVersion.create(
                            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
                    reporter.warning(
                        "Running R8 version "
                            + Version.getVersionString()
                            + ", which cannot be represented as a semantic version. Using"
                            + " an artificial version newer than any known version for selecting"
                            + " Proguard configurations embedded under META-INF/. This means that"
                            + " all rules with a '-upto-' qualifier will be excluded and all rules"
                            + " with a -from- qualifier will be included.");
                  }
                  return compilerVersion;
                });

        while (!providers.isEmpty()) {
          DataResourceProvider dataResourceProvider = providers.pop().getDataResourceProvider();
          if (dataResourceProvider != null) {
            try {
              ExtractEmbeddedRules embeddedProguardConfigurationVisitor =
                  new ExtractEmbeddedRules(reporter, semanticVersionSupplier);
              dataResourceProvider.accept(embeddedProguardConfigurationVisitor);
              embeddedProguardConfigurationVisitor.parseRelevantRules(parser);
            } catch (ResourceException e) {
              reporter.error(new ExceptionDiagnostic(e));
            }
          }
        }
      }
    }

    private void extractKeepAnnotationRules(ProguardConfigurationParser parser) {
      if (!enableExperimentalKeepAnnotations) {
        return;
      }
      try {
        for (ProgramResourceProvider provider : getAppBuilder().getProgramResourceProviders()) {
          for (ProgramResource resource : provider.getProgramResources()) {
            if (resource.getKind() == Kind.CF) {
              List<KeepDeclaration> declarations =
                  KeepEdgeReader.readKeepEdges(resource.getBytes());
              KeepRuleExtractor extractor =
                  new KeepRuleExtractor(
                      rule -> {
                        ProguardConfigurationSourceStrings source =
                            new ProguardConfigurationSourceStrings(
                                Collections.singletonList(rule), null, resource.getOrigin());
                        parser.parse(source);
                      });
              declarations.forEach(extractor::extract);
            }
          }
        }
      } catch (ResourceException e) {
        throw getAppBuilder().getReporter().fatalError(new ExceptionDiagnostic(e));
      }
    }

    // Internal for-testing method to add post-processors of the proguard configuration.
    void addProguardConfigurationConsumerForTesting(Consumer<ProguardConfiguration.Builder> c) {
      Consumer<ProguardConfiguration.Builder> oldConsumer = proguardConfigurationConsumerForTesting;
      proguardConfigurationConsumerForTesting =
          builder -> {
            if (oldConsumer != null) {
              oldConsumer.accept(builder);
            }
            c.accept(builder);
          };
    }

    void addSyntheticProguardRulesConsumerForTesting(
        Consumer<List<ProguardConfigurationRule>> consumer) {
      syntheticProguardRulesConsumer =
          syntheticProguardRulesConsumer == null
              ? consumer
              : syntheticProguardRulesConsumer.andThen(consumer);

    }

    void setEnableExperimentalCheckEnumUnboxed() {
      parserOptionsBuilder.setEnableExperimentalCheckEnumUnboxed(true);
    }

    void setEnableExperimentalConvertCheckNotNull() {
      parserOptionsBuilder.setEnableExperimentalConvertCheckNotNull(true);
    }

    void setEnableExperimentalWhyAreYouNotInlining() {
      parserOptionsBuilder.setEnableExperimentalWhyAreYouNotInlining(true);
    }

    // Internal for-testing method to allow proguard options only available for testing.
    void setEnableTestProguardOptions() {
      parserOptionsBuilder.setEnableTestingOptions(true);
    }
  }

  // Wrapper class to ensure that R8 does not allow DEX as program inputs.
  private static class EnsureNonDexProgramResourceProvider implements ProgramResourceProvider {

    final ProgramResourceProvider provider;

    public EnsureNonDexProgramResourceProvider(ProgramResourceProvider provider) {
      this.provider = provider;
    }

    @Override
    public Collection<ProgramResource> getProgramResources() throws ResourceException {
      Collection<ProgramResource> resources = provider.getProgramResources();
      for (ProgramResource resource : resources) {
        if (resource.getKind() == Kind.DEX) {
          throw new ResourceException(resource.getOrigin(),
              "R8 does not support compiling DEX inputs");
        }
      }
      return resources;
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return provider.getDataResourceProvider();
    }
  }

  static String getUsageMessage() {
    return R8CommandParser.getUsageMessage();
  }

  private final List<ProguardConfigurationRule> mainDexKeepRules;
  private final ProguardConfiguration proguardConfiguration;
  private final boolean enableTreeShaking;
  private final boolean enableMinification;
  private final boolean disableVerticalClassMerging;
  private final boolean forceProguardCompatibility;
  private final Optional<Boolean> includeDataResources;
  private final StringConsumer proguardMapConsumer;
  private final PartitionMapConsumer partitionMapConsumer;
  private final StringConsumer proguardUsageConsumer;
  private final StringConsumer proguardSeedsConsumer;
  private final StringConsumer proguardConfigurationConsumer;
  private final GraphConsumer keptGraphConsumer;
  private final GraphConsumer mainDexKeptGraphConsumer;
  private final Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer;
  private final StringConsumer desugaredLibraryKeepRuleConsumer;
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final FeatureSplitConfiguration featureSplitConfiguration;
  private final String synthesizedClassPrefix;
  private final boolean enableMissingLibraryApiModeling;
  private final AndroidResourceProvider androidResourceProvider;
  private final AndroidResourceConsumer androidResourceConsumer;
  private final ResourceShrinkerConfiguration resourceShrinkerConfiguration;

  /** Get a new {@link R8Command.Builder}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Get a new {@link R8Command.Builder} using a custom defined diagnostics handler. */
  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  // Internal builder to start from an existing AndroidApp.
  static Builder builder(AndroidApp app) {
    return new Builder(app);
  }

  // Internal builder to start from an existing AndroidApp.
  static Builder builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
    return new Builder(app, diagnosticsHandler);
  }

  /**
   * Parse the R8 command-line.
   *
   * Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return R8CommandParser.parse(args, origin);
  }

  /**
   * Parse the R8 command-line.
   *
   * Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return R8CommandParser.parse(args, origin, handler);
  }

  /** Get the help description for the R8 supported flags. */
  public static List<ParseFlagInfo> getParseFlagsInformation() {
    return ImmutableList.copyOf(R8CommandParser.getFlags());
  }

  private R8Command(
      AndroidApp inputApp,
      ProgramConsumer programConsumer,
      List<ProguardConfigurationRule> mainDexKeepRules,
      StringConsumer mainDexListConsumer,
      ProguardConfiguration proguardConfiguration,
      CompilationMode mode,
      int minApiLevel,
      Reporter reporter,
      DesugarState enableDesugaring,
      boolean enableTreeShaking,
      boolean enableMinification,
      boolean disableVerticalClassMerging,
      boolean forceProguardCompatibility,
      Optional<Boolean> includeDataResources,
      StringConsumer proguardMapConsumer,
      PartitionMapConsumer partitionMapConsumer,
      StringConsumer proguardUsageConsumer,
      StringConsumer proguardSeedsConsumer,
      StringConsumer proguardConfigurationConsumer,
      GraphConsumer keptGraphConsumer,
      GraphConsumer mainDexKeptGraphConsumer,
      Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer,
      boolean optimizeMultidexForLinearAlloc,
      boolean encodeChecksum,
      BiPredicate<String, Long> dexClassChecksumFilter,
      StringConsumer desugaredLibraryKeepRuleConsumer,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      FeatureSplitConfiguration featureSplitConfiguration,
      List<AssertionsConfiguration> assertionsConfiguration,
      List<Consumer<Inspector>> outputInspections,
      String synthesizedClassPrefix,
      int threadCount,
      DumpInputFlags dumpInputFlags,
      MapIdProvider mapIdProvider,
      SourceFileProvider sourceFileProvider,
      boolean enableMissingLibraryApiModeling,
      boolean isAndroidPlatformBuild,
      List<ArtProfileForRewriting> artProfilesForRewriting,
      List<StartupProfileProvider> startupProfileProviders,
      ClassConflictResolver classConflictResolver,
      CancelCompilationChecker cancelCompilationChecker,
      AndroidResourceProvider androidResourceProvider,
      AndroidResourceConsumer androidResourceConsumer,
      ResourceShrinkerConfiguration resourceShrinkerConfiguration) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        reporter,
        enableDesugaring,
        optimizeMultidexForLinearAlloc,
        encodeChecksum,
        dexClassChecksumFilter,
        assertionsConfiguration,
        outputInspections,
        threadCount,
        dumpInputFlags,
        mapIdProvider,
        sourceFileProvider,
        isAndroidPlatformBuild,
        artProfilesForRewriting,
        startupProfileProviders,
        classConflictResolver,
        cancelCompilationChecker);
    assert proguardConfiguration != null;
    assert mainDexKeepRules != null;
    this.mainDexKeepRules = mainDexKeepRules;
    this.proguardConfiguration = proguardConfiguration;
    this.enableTreeShaking = enableTreeShaking;
    this.enableMinification = enableMinification;
    this.disableVerticalClassMerging = disableVerticalClassMerging;
    this.forceProguardCompatibility = forceProguardCompatibility;
    this.includeDataResources = includeDataResources;
    this.proguardMapConsumer = proguardMapConsumer;
    this.partitionMapConsumer = partitionMapConsumer;
    this.proguardUsageConsumer = proguardUsageConsumer;
    this.proguardSeedsConsumer = proguardSeedsConsumer;
    this.proguardConfigurationConsumer = proguardConfigurationConsumer;
    this.keptGraphConsumer = keptGraphConsumer;
    this.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;
    this.syntheticProguardRulesConsumer = syntheticProguardRulesConsumer;
    this.desugaredLibraryKeepRuleConsumer = desugaredLibraryKeepRuleConsumer;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.featureSplitConfiguration = featureSplitConfiguration;
    this.synthesizedClassPrefix = synthesizedClassPrefix;
    this.enableMissingLibraryApiModeling = enableMissingLibraryApiModeling;
    this.androidResourceProvider = androidResourceProvider;
    this.androidResourceConsumer = androidResourceConsumer;
    this.resourceShrinkerConfiguration = resourceShrinkerConfiguration;
  }

  private R8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    mainDexKeepRules = ImmutableList.of();
    proguardConfiguration = null;
    enableTreeShaking = false;
    enableMinification = false;
    disableVerticalClassMerging = false;
    forceProguardCompatibility = false;
    includeDataResources = null;
    proguardMapConsumer = null;
    partitionMapConsumer = null;
    proguardUsageConsumer = null;
    proguardSeedsConsumer = null;
    proguardConfigurationConsumer = null;
    keptGraphConsumer = null;
    mainDexKeptGraphConsumer = null;
    syntheticProguardRulesConsumer = null;
    desugaredLibraryKeepRuleConsumer = null;
    desugaredLibrarySpecification = null;
    featureSplitConfiguration = null;
    synthesizedClassPrefix = null;
    enableMissingLibraryApiModeling = false;
    androidResourceProvider = null;
    androidResourceConsumer = null;
    resourceShrinkerConfiguration = null;
  }

  public DexItemFactory getDexItemFactory() {
    return proguardConfiguration.getDexItemFactory();
  }

  /** Get the enable-tree-shaking state. */
  public boolean getEnableTreeShaking() {
    return enableTreeShaking;
  }

  /** Get the enable-minification state. */
  public boolean getEnableMinification() {
    return enableMinification;
  }

  /** Get the Proguard compatibility state. */
  public boolean getProguardCompatibility() {
    return forceProguardCompatibility;
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(getMode(), proguardConfiguration, getReporter());
    assert !internal.testing.allowOutlinerInterfaceArrayArguments;  // Only allow in tests.
    internal.programConsumer = getProgramConsumer();
    internal.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(getMinApiLevel()));
    internal.desugarState = getDesugarState();
    assert internal.isShrinking() == getEnableTreeShaking();
    assert internal.isMinifying() == getEnableMinification();
    assert !internal.ignoreMissingClasses;
    internal.ignoreMissingClasses =
        proguardConfiguration.isIgnoreWarnings()
            || (forceProguardCompatibility
                && !internal.isOptimizing()
                && !internal.isShrinking()
                && !internal.isMinifying());

    assert !internal.verbose;
    internal.mainDexKeepRules = mainDexKeepRules;
    internal.minimalMainDex = internal.debug;
    internal.mainDexListConsumer = getMainDexListConsumer();
    internal.lineNumberOptimization =
        (internal.isOptimizing() || internal.isMinifying())
            ? LineNumberOptimization.ON
            : LineNumberOptimization.OFF;
    MappingComposeOptions mappingComposeOptions = internal.mappingComposeOptions();
    mappingComposeOptions.enableExperimentalMappingComposition = true;

    HorizontalClassMergerOptions horizontalClassMergerOptions =
        internal.horizontalClassMergerOptions();
    assert internal.isOptimizing() || horizontalClassMergerOptions.isRestrictedToSynthetics();

    assert !internal.enableTreeShakingOfLibraryMethodOverrides;
    assert internal.enableVerticalClassMerging || !internal.isOptimizing();

    if (!internal.isShrinking()) {
      // If R8 is not shrinking, there is no point in running various optimizations since the
      // optimized classes will still remain in the program (the application size could increase).
      internal.enableEnumUnboxing = false;
      internal.enableVerticalClassMerging = false;
    }

    // Amend the proguard-map consumer with options from the proguard configuration.
    StringConsumer stringConsumer =
        wrapStringConsumer(
            proguardMapConsumer,
            proguardConfiguration.isPrintMapping(),
            proguardConfiguration.getPrintMappingFile());
    MapConsumer mapConsumer =
        wrapExistingMapConsumerIfNotNull(
            internal.mapConsumer, partitionMapConsumer, MapConsumerToPartitionMapConsumer::new);
    internal.mapConsumer =
        wrapExistingMapConsumerIfNotNull(
            mapConsumer,
            stringConsumer,
            nonNullStringConsumer ->
                ProguardMapStringConsumer.builder().setStringConsumer(stringConsumer).build());

    // Amend the usage information consumer with options from the proguard configuration.
    internal.usageInformationConsumer =
        wrapStringConsumer(
            proguardUsageConsumer,
            proguardConfiguration.isPrintUsage(),
            proguardConfiguration.getPrintUsageFile());

    // Amend the pg-seeds consumer with options from the proguard configuration.
    internal.proguardSeedsConsumer =
        wrapStringConsumer(
            proguardSeedsConsumer,
            proguardConfiguration.isPrintSeeds(),
            proguardConfiguration.getSeedFile());

    // Amend the configuration consumer with options from the proguard configuration.
    internal.configurationConsumer =
        wrapStringConsumer(
            proguardConfigurationConsumer,
            proguardConfiguration.isPrintConfiguration(),
            proguardConfiguration.getPrintConfigurationFile());

    // Set the kept-graph consumer if any. It will only be actively used if the enqueuer triggers.
    internal.keptGraphConsumer = keptGraphConsumer;
    internal.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;

    internal.dataResourceConsumer = internal.programConsumer.getDataResourceConsumer();

    internal.featureSplitConfiguration = featureSplitConfiguration;

    internal.syntheticProguardRulesConsumer = syntheticProguardRulesConsumer;

    internal.outputInspections = InspectorImpl.wrapInspections(getOutputInspections());

    if (!enableMissingLibraryApiModeling) {
      internal.apiModelingOptions().disableApiCallerIdentification();
      internal.apiModelingOptions().disableOutliningAndStubbing();
    }

    // Default is to remove all javac generated assertion code when generating dex.
    assert internal.assertionsConfiguration == null;
    AssertionsConfiguration.Builder builder = AssertionsConfiguration.builder(getReporter());
    internal.assertionsConfiguration =
        new AssertionConfigurationWithDefault(
            getProgramConsumer() instanceof ClassFileConsumer
                ? AssertionsConfiguration.Builder.passthroughAllAssertions(builder)
                : AssertionsConfiguration.Builder.compileTimeDisableAllAssertions(builder),
            getAssertionsConfiguration());

    // TODO(b/171552739): Enable class merging for CF. When compiling libraries, we need to be
    //  careful when merging a public member 'm' from a class A into another class B, since B could
    //  have a kept subclass, in which case 'm' would leak into the public API.
    if (internal.isGeneratingClassFiles()) {
      horizontalClassMergerOptions.disable();
      // R8 CF output does not support desugaring so disable it.
      internal.desugarState = DesugarState.OFF;
    }

    // EXPERIMENTAL flags.
    assert !internal.forceProguardCompatibility;
    internal.forceProguardCompatibility = forceProguardCompatibility;
    if (disableVerticalClassMerging) {
      internal.enableVerticalClassMerging = false;
    }

    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    internal.configureDesugaredLibrary(desugaredLibrarySpecification, synthesizedClassPrefix);
    boolean l8Shrinking = !internal.synthesizedClassPrefix.isEmpty();
    // TODO(b/214382176): Enable all the time.
    internal.loadAllClassDefinitions = l8Shrinking;
    if (l8Shrinking) {
      internal.apiModelingOptions().disableStubbingOfClasses();
    }
    internal.desugaredLibraryKeepRuleConsumer = desugaredLibraryKeepRuleConsumer;

    // Set up the map and source file providers.
    // Note that minify/optimize settings must be set on internal options before doing this.
    internal.mapIdProvider = getMapIdProvider();
    internal.sourceFileProvider =
        SourceFileRewriter.computeSourceFileProvider(
            getSourceFileProvider(), proguardConfiguration, internal);

    internal.configureAndroidPlatformBuild(getAndroidPlatformBuild());

    internal.getArtProfileOptions().setArtProfilesForRewriting(getArtProfilesForRewriting());
    if (!getStartupProfileProviders().isEmpty()) {
      internal.getStartupOptions().setStartupProfileProviders(getStartupProfileProviders());
    }

    internal.programClassConflictResolver =
        ProgramClassCollection.wrappedConflictResolver(
            getClassConflictResolver(), internal.reporter);

    internal.cancelCompilationChecker = getCancelCompilationChecker();

    internal.androidResourceProvider = androidResourceProvider;
    internal.androidResourceConsumer = androidResourceConsumer;
    internal.resourceShrinkerConfiguration = resourceShrinkerConfiguration;

    if (!DETERMINISTIC_DEBUGGING) {
      assert internal.threadCount == ThreadUtils.NOT_SPECIFIED;
      internal.threadCount = getThreadCount();
    }

    internal.tool = Tool.R8;

    internal.setDumpInputFlags(getDumpInputFlags());
    internal.dumpOptions = dumpOptions();

    return internal;
  }

  private static StringConsumer wrapStringConsumer(
      StringConsumer optionConsumer, boolean optionsFlag, Path optionFile) {
    if (optionsFlag) {
      if (optionFile != null) {
        return new StringConsumer.FileConsumer(optionFile, optionConsumer);
      } else {
        return new StandardOutConsumer(optionConsumer);
      }
    }
    return optionConsumer;
  }

  private static class StandardOutConsumer extends StringConsumer.ForwardingConsumer {

    public StandardOutConsumer(StringConsumer consumer) {
      super(consumer);
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      super.accept(string, handler);
      System.out.print(string);
    }
  }

  private DumpOptions dumpOptions() {
    DumpOptions.Builder builder = DumpOptions.builder(Tool.R8).readCurrentSystemProperties();
    dumpBaseCommandOptions(builder);
    return builder
        .setIncludeDataResources(includeDataResources)
        .setTreeShaking(getEnableTreeShaking())
        .setMinification(getEnableMinification())
        .setForceProguardCompatibility(forceProguardCompatibility)
        .setFeatureSplitConfiguration(featureSplitConfiguration)
        .setProguardConfiguration(proguardConfiguration)
        .setMainDexKeepRules(mainDexKeepRules)
        .setDesugaredLibraryConfiguration(desugaredLibrarySpecification)
        .setEnableMissingLibraryApiModeling(enableMissingLibraryApiModeling)
        .build();
  }

  private static class ExtractEmbeddedRules implements DataResourceProvider.Visitor {

    private final Supplier<SemanticVersion> compilerVersionSupplier;
    private final Reporter reporter;
    private final List<ProguardConfigurationSource> proguardSources = new ArrayList<>();
    private final List<ProguardConfigurationSource> r8Sources = new ArrayList<>();
    private SemanticVersion compilerVersion;

    public ExtractEmbeddedRules(
        Reporter reporter, Supplier<SemanticVersion> compilerVersionSupplier) {
      this.compilerVersionSupplier = compilerVersionSupplier;
      this.reporter = reporter;
    }

    @Override
    public void visit(DataDirectoryResource directory) {
      // Don't do anything.
    }

    @Override
    public void visit(DataEntryResource resource) {
      if (relevantProguardResource(resource)) {
        assert !relevantR8Resource(resource);
        readProguardConfigurationSource(resource, proguardSources::add);
      } else if (relevantR8Resource(resource)) {
        assert !relevantProguardResource(resource);
        readProguardConfigurationSource(resource, r8Sources::add);
      }
    }

    private void readProguardConfigurationSource(
        DataEntryResource resource, Consumer<ProguardConfigurationSource> consumer) {
      try (InputStream in = resource.getByteStream()) {
        consumer.accept(new ProguardConfigurationSourceBytes(in, resource.getOrigin()));
      } catch (ResourceException e) {
        reporter.error(
            new StringDiagnostic("Failed to open input: " + e.getMessage(), resource.getOrigin()));
      } catch (Exception e) {
        reporter.error(new ExceptionDiagnostic(e, resource.getOrigin()));
      }
    }

    private boolean relevantProguardResource(DataEntryResource resource) {
      // Configurations in META-INF/com.android.tools/proguard/ are ignored.
      final String proguardPrefix = "META-INF/proguard";
      if (!resource.getName().startsWith(proguardPrefix)) {
        return false;
      }
      String withoutPrefix = resource.getName().substring(proguardPrefix.length());
      return withoutPrefix.startsWith("/");
    }

    private boolean relevantR8Resource(DataEntryResource resource) {
      final String r8Prefix = "META-INF/com.android.tools/r8";
      if (!resource.getName().startsWith(r8Prefix)) {
        return false;
      }
      String withoutPrefix = resource.getName().substring(r8Prefix.length());
      if (withoutPrefix.startsWith("/")) {
        // Everything under META-INF/com.android.tools/r8/ is included (not version specific).
        return true;
      }
      // Expect one of the following patterns:
      //   com.android.tools/r8-from-1.5.0/
      //   com.android.tools/r8-upto-1.6.0/
      //   com.android.tools/r8-from-1.5.0-upto-1.6.0/
      final String fromPrefix = "-from-";
      final String uptoPrefix = "-upto-";
      if (!withoutPrefix.startsWith(fromPrefix) && !withoutPrefix.startsWith(uptoPrefix)) {
        return false;
      }

      SemanticVersion from = SemanticVersion.min();
      SemanticVersion upto = null;

      if (withoutPrefix.startsWith(fromPrefix)) {
        withoutPrefix = withoutPrefix.substring(fromPrefix.length());
        int versionEnd = StringUtils.indexOf(withoutPrefix, '-', '/');
        if (versionEnd == -1) {
          return false;
        }
        try {
          from = SemanticVersion.parse(withoutPrefix.substring(0, versionEnd));
        } catch (IllegalArgumentException e) {
          return false;
        }
        withoutPrefix = withoutPrefix.substring(versionEnd);
      }
      if (withoutPrefix.startsWith(uptoPrefix)) {
        withoutPrefix = withoutPrefix.substring(uptoPrefix.length());
        int versionEnd = withoutPrefix.indexOf('/');
        if (versionEnd == -1) {
          return false;
        }
        try {
          upto = SemanticVersion.parse(withoutPrefix.substring(0, versionEnd));
        } catch (IllegalArgumentException e) {
          return false;
        }
      }
      if (compilerVersion == null) {
        compilerVersion = compilerVersionSupplier.get();
      }
      return compilerVersion.isNewerOrEqual(from)
          && (upto == null || upto.isNewer(compilerVersion));
    }

    private void parse(
        List<ProguardConfigurationSource> sources, ProguardConfigurationParser parser) {
      for (ProguardConfigurationSource source : sources) {
        try {
          parser.parse(source);
        } catch (Exception e) {
          reporter.error(new ExceptionDiagnostic(e, source.getOrigin()));
        }
      }
    }

    void parseRelevantRules(ProguardConfigurationParser parser) {
      parse(!r8Sources.isEmpty() ? r8Sources : proguardSources, parser);
    }
  }
}
