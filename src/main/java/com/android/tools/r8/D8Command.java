// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.InternalOptions.DETERMINISTIC_DEBUGGING;
import static com.android.tools.r8.utils.MapConsumerUtils.wrapExistingMapConsumerIfNotNull;

import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.naming.ProguardMapStringConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalGlobalSyntheticsProgramProvider;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.InternalOptions.MappingComposeOptions;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Immutable command structure for an invocation of the {@link D8} compiler.
 *
 * <p>To build a D8 command use the {@link D8Command.Builder} class. For example:
 *
 * <pre>
 *   D8Command command = D8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .setOutput(Paths.get("output.zip", OutputMode.DexIndexed))
 *     .build();
 * </pre>
 */
@KeepForApi
public final class D8Command extends BaseCompilerCommand {

  private static class DefaultD8DiagnosticsHandler implements DiagnosticsHandler {

    @Override
    public void error(Diagnostic error) {
      if (error instanceof DexFileOverflowDiagnostic) {
        DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
        if (!overflowDiagnostic.hasMainDexSpecification()) {
          DiagnosticsHandler.super.error(
              new StringDiagnostic(
                  overflowDiagnostic.getDiagnosticMessage() + ". Try supplying a main-dex list"));
          return;
        }
      }
      DiagnosticsHandler.super.error(error);
    }
  }

  /**
   * Builder for constructing a D8Command.
   *
   * <p>A builder is obtained by calling {@link D8Command#builder}.
   */
  @KeepForApi
  public static class Builder extends BaseCompilerCommand.Builder<D8Command, Builder> {

    private boolean intermediate = false;
    private GlobalSyntheticsConsumer globalSyntheticsConsumer = null;
    private final List<GlobalSyntheticsResourceProvider> globalSyntheticsResourceProviders =
        new ArrayList<>();
    private DesugarGraphConsumer desugarGraphConsumer = null;
    private SyntheticInfoConsumer syntheticInfoConsumer = null;
    private StringConsumer desugaredLibraryKeepRuleConsumer = null;
    private String synthesizedClassPrefix = "";
    private boolean enableMainDexListCheck = true;
    private boolean minimalMainDex = false;
    private final List<ProguardConfigurationSource> mainDexRules = new ArrayList<>();
    private boolean enableMissingLibraryApiModeling = false;
    private boolean enableRewritingOfArtProfilesIsNopCheck = false;

    private Builder() {
      this(new DefaultD8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    private Builder(AndroidApp app) {
      super(app);
    }

    /**
     * Add dex program-data.
     */
    @Override
    public Builder addDexProgramData(byte[] data, Origin origin) {
      guard(() -> getAppBuilder().addDexProgramData(data, origin));
      return self();
    }

    /**
     * Add classpath file resources. These have @Override to ensure binary compatibility.
     */
    @Override
    public Builder addClasspathFiles(Path... files) {
      return super.addClasspathFiles(files);
    }

    /**
     * Add classpath file resources.
     */
    @Override
    public Builder addClasspathFiles(Collection<Path> files) {
      return super.addClasspathFiles(files);
    }

    /**
     * Add classfile resources provider for class-path resources.
     */
    @Override
    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      return super.addClasspathResourceProvider(provider);
    }

    /**
     * Set input proguard map used for distribution of classes in multi-dex. Use {@link
     * #setProguardMapInputFile}
     */
    @Deprecated()
    public Builder setProguardInputMapFile(Path proguardInputMap) {
      getAppBuilder().setProguardMapInputData(proguardInputMap);
      return self();
    }

    /** Set input proguard map used for distribution of classes in multi-dex. */
    public Builder setProguardMapInputFile(Path proguardInputMap) {
      getAppBuilder().setProguardMapInputData(proguardInputMap);
      return self();
    }

    /**
     * Set a consumer for receiving the proguard-map content.
     *
     * <p>Note that when a proguard-map consumer is specified for a release build, the compiler will
     * optimize the line-number information and obtaining a source-level stacktrace will require the
     * use of a retrace tool exactly as is needed for programs built by R8.
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
     * Set an output destination to which proguard-map content should be written.
     *
     * <p>Note that when a proguard-map output is specified for a release build, the compiler will
     * optimize the line-number information and obtaining a source-level stacktrace will require the
     * use of a retrace tool exactly as is needed for programs built by R8.
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

    /**
     * Set an output destination to which partition-map content should be written.
     *
     * <p>Note that when a proguard-map output is specified for a release build, the compiler will
     * optimize the line-number information and obtaining a source-level stacktrace will require the
     * use of a retrace tool exactly as is needed for programs built by R8.
     *
     * <p>This is a short-hand for setting a {@link PartitionMapConsumer} using {@link
     * #setPartitionMapConsumer}. Note that any subsequent call to this method or {@link
     * #setPartitionMapConsumer} will override the previous setting.
     *
     * @param partitionMapOutput File-system path to write output at.
     */
    @Override
    public Builder setPartitionMapOutputPath(Path partitionMapOutput) {
      assert partitionMapOutput != null;
      return super.setPartitionMapOutputPath(partitionMapOutput);
    }

    /**
     * Set a consumer for receiving the partition map content.
     *
     * <p>Note that when a proguard-map output is specified for a release build, the compiler will
     * optimize the line-number information and obtaining a source-level stacktrace will require the
     * use of a retrace tool exactly as is needed for programs built by R8.
     *
     * <p>Note that any subsequent call to this method or {@link #setPartitionMapOutputPath} will
     * override the previous setting.
     *
     * @param partitionMapConsumer Consumer to receive the content once produced.
     */
    @Override
    public Builder setPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
      assert partitionMapConsumer != null;
      return super.setPartitionMapConsumer(partitionMapConsumer);
    }

    /**
     * Indicate if compilation is to intermediate results, i.e., intended for later merging.
     *
     * <p>When compiling to intermediate mode, the compiler will avoid sharing of synthetic items,
     * and instead annotate them as synthetics for possible later merging. For global synthetics,
     * the compiler will emit these to a separate consumer (see {@code GlobalSyntheticsConsumer}
     * with the expectation that a later build step will consume them again as part of a
     * non-intermediate build (see {@code GlobalSyntheticsResourceProvider}. Synthetic items
     * typically come from the desugaring of various language features, such as lambdas and default
     * interface methods. Global synthetics are non-local in that many compilation units may
     * reference the same synthetic. For example, desugaring records requires a global tag to
     * distinguish the class of all records.
     *
     * <p>Intermediate mode is implied if compiling results to a "file-per-class-file".
     */
    public Builder setIntermediate(boolean value) {
      this.intermediate = value;
      return self();
    }

    /**
     * Set a consumer for receiving the global synthetic content for the given compilation.
     *
     * <p>Note: this consumer is ignored if the compilation is not an "intermediate mode"
     * compilation.
     */
    public Builder setGlobalSyntheticsConsumer(GlobalSyntheticsConsumer globalSyntheticsConsumer) {
      this.globalSyntheticsConsumer = globalSyntheticsConsumer;
      return self();
    }

    /** Add global synthetics resource providers. */
    public Builder addGlobalSyntheticsResourceProviders(
        GlobalSyntheticsResourceProvider... providers) {
      return addGlobalSyntheticsResourceProviders(Arrays.asList(providers));
    }

    /** Add global synthetics resource providers. */
    public Builder addGlobalSyntheticsResourceProviders(
        Collection<GlobalSyntheticsResourceProvider> providers) {
      providers.forEach(globalSyntheticsResourceProviders::add);
      return self();
    }

    /** Add global synthetics resource files. */
    public Builder addGlobalSyntheticsFiles(Path... files) {
      return addGlobalSyntheticsFiles(Arrays.asList(files));
    }

    /** Add global synthetics resource files. */
    public Builder addGlobalSyntheticsFiles(Collection<Path> files) {
      for (Path file : files) {
        addGlobalSyntheticsResourceProviders(new GlobalSyntheticsResourceFile(file));
      }
      return self();
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
     * Get the consumer that will receive dependency information for desugaring.
     */
    public DesugarGraphConsumer getDesugarGraphConsumer() {
      return desugarGraphConsumer;
    }

    /**
     * Set the consumer that will receive dependency information for desugaring.
     *
     * <p>Setting the consumer will clear any previously set consumer.
     */
    public Builder setDesugarGraphConsumer(DesugarGraphConsumer desugarGraphConsumer) {
      this.desugarGraphConsumer = desugarGraphConsumer;
      return self();
    }

    /** Get the consumer that will receive information about compiler synthesized classes. */
    public SyntheticInfoConsumer getSyntheticInfoConsumer() {
      return syntheticInfoConsumer;
    }

    /**
     * Set the consumer that will receive information about compiler synthesized classes.
     *
     * <p>Setting the consumer will clear any previously set consumer.
     */
    public Builder setSyntheticInfoConsumer(SyntheticInfoConsumer syntheticInfoConsumer) {
      this.syntheticInfoConsumer = syntheticInfoConsumer;
      return self();
    }

    /**
     * Add a collection of startup profile providers that should be used for distributing the
     * program classes in dex.
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
     * program classes in dex.
     *
     * <p>NOTE: Startup profiles are ignored when compiling to class files or the min-API level does
     * not support native multidex (API<=20).
     */
    @Override
    public Builder addStartupProfileProviders(
        Collection<StartupProfileProvider> startupProfileProviders) {
      return super.addStartupProfileProviders(startupProfileProviders);
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.DEBUG;
    }

    Builder setSynthesizedClassesPrefix(String prefix) {
      synthesizedClassPrefix = prefix;
      return self();
    }

    @Deprecated
    // Internal helper for supporting bazel integration.
    Builder setEnableMainDexListCheck(boolean value) {
      enableMainDexListCheck = value;
      return self();
    }

    @Deprecated
    // Internal helper for supporting bazel integration.
    Builder setMinimalMainDex(boolean value) {
      minimalMainDex = value;
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

    Builder setEnableRewritingOfArtProfilesIsNopCheck() {
      enableRewritingOfArtProfilesIsNopCheck = true;
      return self();
    }

    @Override
    void validate() {
      if (isPrintHelp()) {
        return;
      }
      Reporter reporter = getReporter();
      if (getAppBuilder().hasMainDexList()) {
        if (intermediate) {
          reporter.error("Option --main-dex-list cannot be used with --intermediate");
        }
        if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
          reporter.error("Option --main-dex-list cannot be used with --file-per-class");
        }
      }
      if (!mainDexRules.isEmpty()) {
        if (intermediate) {
          reporter.error("Option --main-dex-rules cannot be used with --intermediate");
        }
        if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
          reporter.error("Option --main-dex-rules cannot be used with --file-per-class");
        }
      }
      if (getMainDexListConsumer() != null
          && mainDexRules.isEmpty()
          && !getAppBuilder().hasMainDexList()) {
        reporter.error(
            "Option --main-dex-list-output requires --main-dex-rules and/or --main-dex-list");
      }
      if (getMinApiLevel() >= AndroidApiLevel.L.getLevel()) {
        if (getMainDexListConsumer() != null || getAppBuilder().hasMainDexList()) {
          reporter.error(
              "D8 does not support main-dex inputs and outputs when compiling to API level "
                  + AndroidApiLevel.L.getLevel()
                  + " and above");
        }
      }
      if (hasDesugaredLibraryConfiguration() && getDisableDesugaring()) {
        reporter.error("Using desugared library configuration requires desugaring to be enabled");
      }
      if (getProgramConsumer() instanceof ClassFileConsumer
          && getDisableDesugaring()
          && isMinApiLevelSet()) {
        reporter.error("Compiling to CF with --min-api and --no-desugaring is not supported");
      }
      if (!getStartupProfileProviders().isEmpty()) {
        if (intermediate) {
          reporter.error("D8 startup layout is not supported in intermediate mode");
        }
        if (getMinApiLevel() < AndroidApiLevel.L.getLevel()) {
          reporter.error(
              "D8 startup layout requires native multi dex support (API level "
                  + AndroidApiLevel.L.getLevel()
                  + " and above)");
        }
      }
      super.validate();
    }

    @Override
    D8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new D8Command(isPrintHelp(), isPrintVersion());
      }

      intermediate |= getProgramConsumer() instanceof DexFilePerClassFileConsumer;

      DexItemFactory factory = new DexItemFactory();
      DesugaredLibrarySpecification desugaredLibrarySpecification =
          getDesugaredLibraryConfiguration(factory, false);

      ImmutableList<ProguardConfigurationRule> mainDexKeepRules =
          ProguardConfigurationParser.parse(mainDexRules, factory, getReporter());

      if (!globalSyntheticsResourceProviders.isEmpty()) {
        addProgramResourceProvider(
            new InternalGlobalSyntheticsProgramProvider(globalSyntheticsResourceProviders));
      }

      // If compiling to CF with --no-desugaring then the target API is B for consistency with R8.
      int minApiLevel =
          getProgramConsumer() instanceof ClassFileConsumer && getDisableDesugaring()
              ? AndroidApiLevel.B.getLevel()
              : getMinApiLevel();

      return new D8Command(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMainDexListConsumer(),
          minApiLevel,
          getReporter(),
          getDesugaringState(),
          intermediate,
          intermediate ? globalSyntheticsConsumer : null,
          isOptimizeMultidexForLinearAlloc(),
          getIncludeClassesChecksum(),
          getDexClassChecksumFilter(),
          getDesugarGraphConsumer(),
          getSyntheticInfoConsumer(),
          desugaredLibraryKeepRuleConsumer,
          desugaredLibrarySpecification,
          getAssertionsConfiguration(),
          getOutputInspections(),
          synthesizedClassPrefix,
          enableMainDexListCheck,
          minimalMainDex,
          mainDexKeepRules,
          getThreadCount(),
          getDumpInputFlags(),
          getMapIdProvider(),
          proguardMapConsumer,
          partitionMapConsumer,
          enableMissingLibraryApiModeling,
          enableRewritingOfArtProfilesIsNopCheck,
          getAndroidPlatformBuild(),
          getArtProfilesForRewriting(),
          getStartupProfileProviders(),
          getClassConflictResolver(),
          getCancelCompilationChecker(),
          factory);
    }
  }

  private final boolean intermediate;
  private final GlobalSyntheticsConsumer globalSyntheticsConsumer;
  private final SyntheticInfoConsumer syntheticInfoConsumer;
  private final DesugarGraphConsumer desugarGraphConsumer;
  private final StringConsumer desugaredLibraryKeepRuleConsumer;
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final String synthesizedClassPrefix;
  private final boolean enableMainDexListCheck;
  private final boolean minimalMainDex;
  private final ImmutableList<ProguardConfigurationRule> mainDexKeepRules;
  private final StringConsumer proguardMapConsumer;
  private final PartitionMapConsumer partitionMapConsumer;
  private final boolean enableMissingLibraryApiModeling;
  private final boolean enableRewritingOfArtProfilesIsNopCheck;
  private final DexItemFactory factory;

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  // Internal builder to start from an existing AndroidApp.
  static Builder builder(AndroidApp app) {
    return new Builder(app);
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return D8CommandParser.parse(args, origin);
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return D8CommandParser.parse(args, origin, handler);
  }

  /** Get the help description for the D8 supported flags. */
  public static List<ParseFlagInfo> getParseFlagsInformation() {
    return D8CommandParser.getFlags();
  }

  private D8Command(
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      StringConsumer mainDexListConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      DesugarState enableDesugaring,
      boolean intermediate,
      GlobalSyntheticsConsumer globalSyntheticsConsumer,
      boolean optimizeMultidexForLinearAlloc,
      boolean encodeChecksum,
      BiPredicate<String, Long> dexClassChecksumFilter,
      DesugarGraphConsumer desugarGraphConsumer,
      SyntheticInfoConsumer syntheticInfoConsumer,
      StringConsumer desugaredLibraryKeepRuleConsumer,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      List<AssertionsConfiguration> assertionsConfiguration,
      List<Consumer<Inspector>> outputInspections,
      String synthesizedClassPrefix,
      boolean enableMainDexListCheck,
      boolean minimalMainDex,
      ImmutableList<ProguardConfigurationRule> mainDexKeepRules,
      int threadCount,
      DumpInputFlags dumpInputFlags,
      MapIdProvider mapIdProvider,
      StringConsumer proguardMapConsumer,
      PartitionMapConsumer partitionMapConsumer,
      boolean enableMissingLibraryApiModeling,
      boolean enableRewritingOfArtProfilesIsNopCheck,
      boolean isAndroidPlatformBuild,
      List<ArtProfileForRewriting> artProfilesForRewriting,
      List<StartupProfileProvider> startupProfileProviders,
      ClassConflictResolver classConflictResolver,
      CancelCompilationChecker cancelCompilationChecker,
      DexItemFactory factory) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        diagnosticsHandler,
        enableDesugaring,
        optimizeMultidexForLinearAlloc,
        encodeChecksum,
        dexClassChecksumFilter,
        assertionsConfiguration,
        outputInspections,
        threadCount,
        dumpInputFlags,
        mapIdProvider,
        null,
        isAndroidPlatformBuild,
        artProfilesForRewriting,
        startupProfileProviders,
        classConflictResolver,
        cancelCompilationChecker);
    this.intermediate = intermediate;
    this.globalSyntheticsConsumer = globalSyntheticsConsumer;
    this.syntheticInfoConsumer = syntheticInfoConsumer;
    this.desugarGraphConsumer = desugarGraphConsumer;
    this.desugaredLibraryKeepRuleConsumer = desugaredLibraryKeepRuleConsumer;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.synthesizedClassPrefix = synthesizedClassPrefix;
    this.enableMainDexListCheck = enableMainDexListCheck;
    this.minimalMainDex = minimalMainDex;
    this.mainDexKeepRules = mainDexKeepRules;
    this.proguardMapConsumer = proguardMapConsumer;
    this.partitionMapConsumer = partitionMapConsumer;
    this.enableMissingLibraryApiModeling = enableMissingLibraryApiModeling;
    this.enableRewritingOfArtProfilesIsNopCheck = enableRewritingOfArtProfilesIsNopCheck;
    this.factory = factory;
  }

  private D8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    intermediate = false;
    globalSyntheticsConsumer = null;
    syntheticInfoConsumer = null;
    desugarGraphConsumer = null;
    desugaredLibraryKeepRuleConsumer = null;
    desugaredLibrarySpecification = null;
    synthesizedClassPrefix = null;
    enableMainDexListCheck = true;
    minimalMainDex = false;
    mainDexKeepRules = null;
    proguardMapConsumer = null;
    partitionMapConsumer = null;
    enableMissingLibraryApiModeling = false;
    enableRewritingOfArtProfilesIsNopCheck = false;
    factory = null;
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    if (internal.isGeneratingClassFiles()) {
      // Turn off switch optimizations when generating class files.
      assert internal.enableSwitchRewriting;
      internal.enableSwitchRewriting = false;
      assert internal.enableStringSwitchConversion;
      internal.enableStringSwitchConversion = false;
    } else {
      assert !internal.desugarSpecificOptions().allowAllDesugaredInput
          || getDesugarState() == DesugarState.OFF;
    }
    internal.mainDexListConsumer = getMainDexListConsumer();
    internal.minimalMainDex = internal.debug || minimalMainDex;
    internal.enableMainDexListCheck = enableMainDexListCheck;
    internal.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(getMinApiLevel()));
    internal.intermediate = intermediate;
    internal.retainCompileTimeAnnotations = intermediate;
    internal.setGlobalSyntheticsConsumer(globalSyntheticsConsumer);
    internal.setSyntheticInfoConsumer(syntheticInfoConsumer);
    internal.desugarGraphConsumer = desugarGraphConsumer;
    internal.mainDexKeepRules = mainDexKeepRules;
    MapConsumer mapConsumer =
        wrapExistingMapConsumerIfNotNull(
            internal.mapConsumer, partitionMapConsumer, MapConsumerToPartitionMapConsumer::new);
    internal.mapConsumer =
        wrapExistingMapConsumerIfNotNull(
            mapConsumer,
            proguardMapConsumer,
            nonNullStringConsumer ->
                ProguardMapStringConsumer.builder().setStringConsumer(proguardMapConsumer).build());
    internal.lineNumberOptimization =
        !internal.debug && proguardMapConsumer != null
            ? LineNumberOptimization.ON
            : LineNumberOptimization.OFF;
    MappingComposeOptions mappingComposeOptions = internal.mappingComposeOptions();
    mappingComposeOptions.enableExperimentalMappingComposition = true;
    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;
    internal.passthroughDexCode = true;

    // Assert some of R8 optimizations are disabled.
    assert !internal.inlinerOptions().enableInlining;
    assert !internal.enableClassInlining;
    assert internal.getVerticalClassMergerOptions().isDisabled();
    assert !internal.enableEnumValueOptimization;
    assert !internal.outline.enabled;
    assert !internal.enableTreeShakingOfLibraryMethodOverrides;

    internal.desugarState = getDesugarState();
    internal.encodeChecksums = getIncludeClassesChecksum();
    internal.dexClassChecksumFilter = getDexClassChecksumFilter();
    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    internal.configureDesugaredLibrary(desugaredLibrarySpecification, synthesizedClassPrefix);
    internal.desugaredLibraryKeepRuleConsumer = desugaredLibraryKeepRuleConsumer;

    if (!enableMissingLibraryApiModeling) {
      internal.apiModelingOptions().disableApiCallerIdentification();
      internal.apiModelingOptions().disableOutliningAndStubbing();
    }

    if (enableRewritingOfArtProfilesIsNopCheck) {
      internal.getArtProfileOptions().setEnableNopCheckForTesting();
    }

    // Default is to remove all javac generated assertion code when generating dex.
    assert internal.assertionsConfiguration == null;
    internal.assertionsConfiguration =
        new AssertionConfigurationWithDefault(
            AssertionsConfiguration.builder(getReporter())
                .setCompileTimeDisable()
                .setScopeAll()
                .build(),
            getAssertionsConfiguration());

    internal.outputInspections = InspectorImpl.wrapInspections(getOutputInspections());

    if (!DETERMINISTIC_DEBUGGING) {
      assert internal.threadCount == ThreadUtils.NOT_SPECIFIED;
      internal.threadCount = getThreadCount();
    }

    // Disable global optimizations.
    internal.disableGlobalOptimizations();

    HorizontalClassMergerOptions horizontalClassMergerOptions =
        internal.horizontalClassMergerOptions();
    if (internal.isGeneratingDex()) {
      horizontalClassMergerOptions.setRestrictToSynthetics();
    } else {
      assert internal.isGeneratingClassFiles();
      horizontalClassMergerOptions.disable();
    }

    internal.configureAndroidPlatformBuild(getAndroidPlatformBuild());

    internal.getArtProfileOptions().setArtProfilesForRewriting(getArtProfilesForRewriting());
    if (!getStartupProfileProviders().isEmpty()) {
      internal.getStartupOptions().setStartupProfileProviders(getStartupProfileProviders());
    }

    internal.programClassConflictResolver =
        ProgramClassCollection.wrappedConflictResolver(
            getClassConflictResolver(), internal.reporter);

    internal.cancelCompilationChecker = getCancelCompilationChecker();

    internal.tool = Tool.D8;
    internal.setDumpInputFlags(getDumpInputFlags());
    internal.dumpOptions = dumpOptions();

    return internal;
  }

  private DumpOptions dumpOptions() {
    DumpOptions.Builder builder = DumpOptions.builder(Tool.D8).readCurrentSystemProperties();
    dumpBaseCommandOptions(builder);
    return builder
        .setIntermediate(intermediate)
        .setDesugaredLibraryConfiguration(desugaredLibrarySpecification)
        .setMainDexKeepRules(mainDexKeepRules)
        .setEnableMissingLibraryApiModeling(enableMissingLibraryApiModeling)
        .build();
  }
}
