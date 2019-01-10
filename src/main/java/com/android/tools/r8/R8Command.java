// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.origin.StandardOutOrigin;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceBytes;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
@Keep
public final class R8Command extends BaseCompilerCommand {

  /**
   * Builder for constructing a R8Command.
   *
   * <p>A builder is obtained by calling {@link R8Command#builder}.
   */
  @Keep
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
    private Consumer<ProguardConfiguration.Builder> proguardConfigurationConsumer = null;
    private Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer = null;
    private final List<ProguardConfigurationSource> proguardConfigs = new ArrayList<>();
    private boolean disableTreeShaking = false;
    private boolean disableMinification = false;
    private boolean disableVerticalClassMerging = false;
    private boolean forceProguardCompatibility = false;
    private StringConsumer proguardMapConsumer = null;
    private GraphConsumer keptGraphConsumer = null;
    private GraphConsumer mainDexKeptGraphConsumer = null;

    // Internal compatibility mode for use from CompatProguard tool.
    Path proguardCompatibilityRulesOutput = null;

    private boolean allowPartiallyImplementedProguardOptions = false;
    private boolean allowTestProguardOptions = false;

    // TODO(zerny): Consider refactoring CompatProguardCommandBuilder to avoid subclassing.
    Builder() {
      this(new DefaultR8DiagnosticsHandler());
    }

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    private Builder(AndroidApp app) {
      super(app);
    }

    private Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(app, diagnosticsHandler);
    }

    // Internal

    void internalForceProguardCompatibility() {
      this.forceProguardCompatibility = true;
    }

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
      guard(() -> {
        for (Path path : paths) {
          mainDexRules.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /** Add proguard configuration files with rules for automatic main-dex-list calculation. */
    public Builder addMainDexRulesFiles(Collection<Path> paths) {
      guard(() -> {
        for (Path path : paths) {
          mainDexRules.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /** Add proguard rules for automatic main-dex-list calculation. */
    public Builder addMainDexRules(List<String> lines, Origin origin) {
      guard(() -> mainDexRules.add(
          new ProguardConfigurationSourceStrings(lines, Paths.get("."), origin)));
      return self();
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
    public Builder setProguardMapOutputPath(Path proguardMapOutput) {
      assert proguardMapOutput != null;
      this.proguardMapConsumer = new StringConsumer.FileConsumer(proguardMapOutput);
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
    public Builder setProguardMapConsumer(StringConsumer proguardMapConsumer) {
      this.proguardMapConsumer = proguardMapConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving kept-graph events.
     *
     * @param graphConsumer
     */
    public Builder setKeptGraphConsumer(GraphConsumer graphConsumer) {
      this.keptGraphConsumer = graphConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving kept-graph events for the content of the main-dex output.
     *
     * @param graphConsumer
     */
    public Builder setMainDexKeptGraphConsumer(GraphConsumer graphConsumer) {
      this.mainDexKeptGraphConsumer = graphConsumer;
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
      return super.setOutput(outputPath, outputMode, includeDataResources);
    }

    @Override
    public Builder addProgramResourceProvider(ProgramResourceProvider programProvider) {
      return super.addProgramResourceProvider(
          new EnsureNonDexProgramResourceProvider(programProvider));
    }

    @Override
    protected InternalProgramOutputPathConsumer createProgramOutputConsumer(
        Path path,
        OutputMode mode,
        boolean consumeDataResources) {
      return super.createProgramOutputConsumer(path, mode, consumeDataResources);
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("R8 does not support compiling to a single DEX file per Java class file");
      }
      if (getMainDexListConsumer() != null
          && mainDexRules.isEmpty()
          && !getAppBuilder().hasMainDexList()) {
        reporter.error(
            "Option --main-dex-list-output require --main-dex-rules and/or --main-dex-list");
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
      for (Path file : programFiles) {
        if (FileUtils.isDexFile(file)) {
          reporter.error(new StringDiagnostic(
              "R8 does not support compiling DEX inputs", new PathOrigin(file)));
        }
      }
      if (getProgramConsumer() instanceof ClassFileConsumer && isMinApiLevelSet()) {
        reporter.error("R8 does not support --min-api when compiling to class files");
      }
      super.validate();
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
      ImmutableList<ProguardConfigurationRule> mainDexKeepRules;
      if (this.mainDexRules.isEmpty()) {
        mainDexKeepRules = ImmutableList.of();
      } else {
        ProguardConfigurationParser parser =
            new ProguardConfigurationParser(factory, reporter);
        parser.parse(mainDexRules);
        mainDexKeepRules = parser.getConfig().getRules();
      }

      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(factory, reporter, allowTestProguardOptions);
      if (!proguardConfigs.isEmpty()) {
        parser.parse(proguardConfigs);
      }
      ProguardConfiguration.Builder configurationBuilder = parser.getConfigurationBuilder();
      configurationBuilder.setForceProguardCompatibility(forceProguardCompatibility);

      if (proguardConfigurationConsumer != null) {
        proguardConfigurationConsumer.accept(configurationBuilder);
      }

      // Process Proguard configurations supplied through data resources in the input.
      DataResourceProvider.Visitor embeddedProguardConfigurationVisitor =
          new DataResourceProvider.Visitor() {
            @Override
            public void visit(DataDirectoryResource directory) {
              // Don't do anything.
            }

            @Override
            public void visit(DataEntryResource resource) {
              if (resource.getName().startsWith("META-INF/proguard/")) {
                try (InputStream in = resource.getByteStream()) {
                  ProguardConfigurationSource source =
                      new ProguardConfigurationSourceBytes(in, resource.getOrigin());
                  parser.parse(source);
                } catch (ResourceException e) {
                  reporter.error(new StringDiagnostic("Failed to open input: " + e.getMessage(),
                      resource.getOrigin()));
                } catch (Exception e) {
                  reporter.error(new ExceptionDiagnostic(e, resource.getOrigin()));
                }
              }
            }
          };

      getAppBuilder().getProgramResourceProviders()
          .stream()
          .map(ProgramResourceProvider::getDataResourceProvider)
          .filter(Objects::nonNull)
          .forEach(dataResourceProvider -> {
              try {
                dataResourceProvider.accept(embeddedProguardConfigurationVisitor);
              } catch (ResourceException e) {
                reporter.error(new ExceptionDiagnostic(e));
              }
          });

      if (disableTreeShaking) {
        configurationBuilder.disableShrinking();
      }

      if (disableMinification) {
        configurationBuilder.disableObfuscation();
      }

      ProguardConfiguration configuration = configurationBuilder.build();
      getAppBuilder()
          .addFilteredProgramArchives(configuration.getInjars())
          .addFilteredLibraryArchives(configuration.getLibraryjars());

      assert getProgramConsumer() != null;

      boolean desugaring =
          (getProgramConsumer() instanceof ClassFileConsumer) ? false : !getDisableDesugaring();

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
              proguardMapConsumer,
              proguardCompatibilityRulesOutput,
              keptGraphConsumer,
              mainDexKeptGraphConsumer,
              syntheticProguardRulesConsumer,
              isOptimizeMultidexForLinearAlloc());

      return command;
    }

    // Internal for-testing method to add post-processors of the proguard configuration.
    void addProguardConfigurationConsumerForTesting(Consumer<ProguardConfiguration.Builder> c) {
      Consumer<ProguardConfiguration.Builder> oldConsumer = proguardConfigurationConsumer;
      proguardConfigurationConsumer =
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

    // Internal for-testing method to add post-processors of the proguard configuration.
    void allowPartiallyImplementedProguardOptions() {
      allowPartiallyImplementedProguardOptions = true;
    }

    // Internal for-testing method to allow proguard options only available for testing.
    void allowTestProguardOptions() {
      allowTestProguardOptions = true;
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

  static final String USAGE_MESSAGE = R8CommandParser.USAGE_MESSAGE;

  private final ImmutableList<ProguardConfigurationRule> mainDexKeepRules;
  private final ProguardConfiguration proguardConfiguration;
  private final boolean enableTreeShaking;
  private final boolean enableMinification;
  private final boolean disableVerticalClassMerging;
  private final boolean forceProguardCompatibility;
  private final StringConsumer proguardMapConsumer;
  private final Path proguardCompatibilityRulesOutput;
  private final GraphConsumer keptGraphConsumer;
  private final GraphConsumer mainDexKeptGraphConsumer;
  private final Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer;

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

  private R8Command(
      AndroidApp inputApp,
      ProgramConsumer programConsumer,
      ImmutableList<ProguardConfigurationRule> mainDexKeepRules,
      StringConsumer mainDexListConsumer,
      ProguardConfiguration proguardConfiguration,
      CompilationMode mode,
      int minApiLevel,
      Reporter reporter,
      boolean enableDesugaring,
      boolean enableTreeShaking,
      boolean enableMinification,
      boolean disableVerticalClassMerging,
      boolean forceProguardCompatibility,
      StringConsumer proguardMapConsumer,
      Path proguardCompatibilityRulesOutput,
      GraphConsumer keptGraphConsumer,
      GraphConsumer mainDexKeptGraphConsumer,
      Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer,
      boolean optimizeMultidexForLinearAlloc) {
    super(inputApp, mode, programConsumer, mainDexListConsumer, minApiLevel, reporter,
        enableDesugaring, optimizeMultidexForLinearAlloc);
    assert proguardConfiguration != null;
    assert mainDexKeepRules != null;
    this.mainDexKeepRules = mainDexKeepRules;
    this.proguardConfiguration = proguardConfiguration;
    this.enableTreeShaking = enableTreeShaking;
    this.enableMinification = enableMinification;
    this.disableVerticalClassMerging = disableVerticalClassMerging;
    this.forceProguardCompatibility = forceProguardCompatibility;
    this.proguardMapConsumer = proguardMapConsumer;
    this.proguardCompatibilityRulesOutput = proguardCompatibilityRulesOutput;
    this.keptGraphConsumer = keptGraphConsumer;
    this.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;
    this.syntheticProguardRulesConsumer = syntheticProguardRulesConsumer;
  }

  private R8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    mainDexKeepRules = ImmutableList.of();
    proguardConfiguration = null;
    enableTreeShaking = false;
    enableMinification = false;
    disableVerticalClassMerging = false;
    forceProguardCompatibility = false;
    proguardMapConsumer = null;
    proguardCompatibilityRulesOutput = null;
    keptGraphConsumer = null;
    mainDexKeptGraphConsumer = null;
    syntheticProguardRulesConsumer = null;
  }

  /** Get the enable-tree-shaking state. */
  public boolean getEnableTreeShaking() {
    return enableTreeShaking;
  }

  /** Get the enable-minification state. */
  public boolean getEnableMinification() {
    return enableMinification;
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(proguardConfiguration, getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    internal.minApiLevel = getMinApiLevel();
    internal.enableDesugaring = getEnableDesugaring();
    assert internal.enableMinification;
    internal.enableMinification = getEnableMinification();
    assert internal.enableTreeShaking;
    internal.enableTreeShaking = getEnableTreeShaking();
    // In current implementation we only enable lambda merger if the tree
    // shaking is enabled. This is caused by the fact that we rely on tree
    // shaking for removing the lambda classes which should be revised later.
    internal.enableLambdaMerging = getEnableTreeShaking();
    assert !internal.ignoreMissingClasses;
    internal.ignoreMissingClasses = proguardConfiguration.isIgnoreWarnings()
        // TODO(70706667): We probably only want this in Proguard compatibility mode.
        || (forceProguardCompatibility
            && !proguardConfiguration.isOptimizing()
            && !internal.enableMinification
            && !internal.enableTreeShaking);

    assert !internal.verbose;
    internal.mainDexKeepRules = mainDexKeepRules;
    internal.minimalMainDex = getMode() == CompilationMode.DEBUG;
    internal.mainDexListConsumer = getMainDexListConsumer();
    internal.lineNumberOptimization =
        !internal.debug && (proguardConfiguration.isOptimizing() || internal.enableMinification)
            ? LineNumberOptimization.ON
            : LineNumberOptimization.OFF;

    assert internal.enableHorizontalClassMerging || !proguardConfiguration.isOptimizing();
    assert internal.enableVerticalClassMerging || !proguardConfiguration.isOptimizing();
    if (internal.debug) {
      internal.getProguardConfiguration().getKeepAttributes().lineNumberTable = true;
      internal.getProguardConfiguration().getKeepAttributes().localVariableTable = true;
      internal.getProguardConfiguration().getKeepAttributes().localVariableTypeTable = true;
      // TODO(zerny): Should we support inlining in debug mode? b/62937285
      internal.enableInlining = false;
      internal.enableClassInlining = false;
      internal.enableHorizontalClassMerging = false;
      internal.enableVerticalClassMerging = false;
      internal.enableClassStaticizer = false;
      // TODO(zerny): Should we support outlining in debug mode? b/62937285
      internal.outline.enabled = false;
    }

    // Setup a configuration consumer.
    if (proguardConfiguration.isPrintConfiguration()) {
      internal.configurationConsumer = proguardConfiguration.getPrintConfigurationFile() != null
          ? new StringConsumer.FileConsumer(proguardConfiguration.getPrintConfigurationFile())
          : new StringConsumer.StreamConsumer(StandardOutOrigin.instance(), System.out);
    }

    // Setup a usage information consumer.
    if (proguardConfiguration.isPrintUsage()) {
      internal.usageInformationConsumer = proguardConfiguration.getPrintUsageFile() != null
          ? new StringConsumer.FileConsumer(proguardConfiguration.getPrintUsageFile())
          : new StringConsumer.StreamConsumer(StandardOutOrigin.instance(), System.out);
    }

    // Setup pg-seeds consumer.
    if (proguardConfiguration.isPrintSeeds()) {
      internal.proguardSeedsConsumer =  proguardConfiguration.getSeedFile() != null
          ? new StringConsumer.FileConsumer(proguardConfiguration.getSeedFile())
          : new StringConsumer.StreamConsumer(StandardOutOrigin.instance(), System.out);
    }

    // Amend the proguard-map consumer with options from the proguard configuration.
    {
      StringConsumer wrappedConsumer;
      if (proguardConfiguration.isPrintMapping()) {
        if (proguardConfiguration.getPrintMappingFile() != null) {
          wrappedConsumer =
              new StringConsumer.FileConsumer(
                  proguardConfiguration.getPrintMappingFile(), proguardMapConsumer);
        } else {
          wrappedConsumer =
              new StringConsumer.StreamConsumer(
                  StandardOutOrigin.instance(), System.out, proguardMapConsumer);
        }
      } else {
        wrappedConsumer = proguardMapConsumer;
      }
      internal.proguardMapConsumer = wrappedConsumer;
    }

    // Set the kept-graph consumer if any. It will only be actively used if the enqueuer triggers.
    internal.keptGraphConsumer = keptGraphConsumer;
    internal.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;

    internal.proguardCompatibilityRulesOutput = proguardCompatibilityRulesOutput;
    internal.dataResourceConsumer = internal.programConsumer.getDataResourceConsumer();

    internal.syntheticProguardRulesConsumer = syntheticProguardRulesConsumer;

    // Default is to remove Java assertion code as Dalvik and Art does not reliable support
    // Java assertions. When generation class file output always keep the Java assertions code.
    assert internal.disableAssertions;
    if (internal.isGeneratingClassFiles()) {
      internal.disableAssertions = false;
    }

    // EXPERIMENTAL flags.
    assert !internal.forceProguardCompatibility;
    internal.forceProguardCompatibility = forceProguardCompatibility;
    if (disableVerticalClassMerging) {
      internal.enableVerticalClassMerging = false;
    }

    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    return internal;
  }
}
