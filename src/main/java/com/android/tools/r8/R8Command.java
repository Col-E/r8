// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
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
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
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
public class R8Command extends BaseCompilerCommand {

  /**
   * Builder for constructing a R8Command.
   *
   * <p>A builder is obtained by calling {@link R8Command#builder}.
   */
  public static class Builder extends BaseCompilerCommand.Builder<R8Command, Builder> {

    private final List<ProguardConfigurationSource> mainDexRules = new ArrayList<>();
    private Consumer<ProguardConfiguration.Builder> proguardConfigurationConsumer = null;
    private final List<ProguardConfigurationSource> proguardConfigs = new ArrayList<>();
    private boolean disableTreeShaking = false;
    private boolean disableMinification = false;
    private boolean forceProguardCompatibility = false;
    private StringConsumer proguardMapConsumer = null;

    // Internal compatibility mode for use from CompatProguard tool.
    Path proguardCompatibilityRulesOutput = null;

    private StringConsumer mainDexListConsumer = null;

    // TODO(zerny): Consider refactoring CompatProguardCommandBuilder to avoid subclassing.
    Builder() {}

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    private Builder(AndroidApp app) {
      super(app);
    }

    // Internal

    void internalForceProguardCompatibility() {
      this.forceProguardCompatibility = true;
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

    /**
     * Set an output destination to which main-dex-list content should be written.
     *
     * <p>This is a short-hand for setting a {@link StringConsumer.FileConsumer} using {@link
     * #setMainDexListConsumer}. Note that any subsequent call to this method or {@link
     * #setMainDexListConsumer} will override the previous setting.
     *
     * @param mainDexListOutputPath File-system path to write output at.
     */
    public Builder setMainDexListOutputPath(Path mainDexListOutputPath) {
      mainDexListConsumer = new StringConsumer.FileConsumer(mainDexListOutputPath);
      return self();
    }

    /**
     * Set a consumer for receiving the main-dex-list content.
     *
     * <p>Note that any subsequent call to this method or {@link #setMainDexListOutputPath} will
     * override the previous setting.
     *
     * @param mainDexListConsumer Consumer to receive the content once produced.
     */
    public Builder setMainDexListConsumer(StringConsumer mainDexListConsumer) {
      this.mainDexListConsumer = mainDexListConsumer;
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
      return super.createProgramOutputConsumer(path, mode, false);
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("R8 does not support compiling to a single DEX file per Java class file");
      }
      if (mainDexListConsumer != null
          && mainDexRules.isEmpty()
          && !getAppBuilder().hasMainDexList()) {
        reporter.error(
            "Option --main-dex-list-output require --main-dex-rules and/or --main-dex-list");
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

      ProguardConfigurationParser parser = new ProguardConfigurationParser(factory, reporter);
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
              mainDexListConsumer,
              configuration,
              getMode(),
              getMinApiLevel(),
              reporter,
              desugaring,
              configuration.isShrinking(),
              configuration.isObfuscating(),
              forceProguardCompatibility,
              proguardMapConsumer,
              proguardCompatibilityRulesOutput,
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

  // Internal state to verify parsing properties not enforced by the builder.
  private static class ParseState {
    CompilationMode mode = null;
    OutputMode outputMode = null;
    Path outputPath = null;
    boolean hasDefinedApiLevel = false;
  }

  static final String USAGE_MESSAGE = String.join("\n", ImmutableList.of(
      "Usage: r8 [options] <input-files>",
      " where <input-files> are any combination of dex, class, zip, jar, or apk files",
      " and options are:",
      "  --release                # Compile without debugging information (default).",
      "  --debug                  # Compile with debugging information.",
      // TODO(b/65390962): Add help for output-mode flags once the CF backend is complete.
      //"  --dex                    # Compile program to DEX file format (default).",
      //"  --classfile              # Compile program to Java classfile format.",
      "  --output <file>          # Output result in <file>.",
      "                           # <file> must be an existing directory or a zip file.",
      "  --lib <file>             # Add <file> as a library resource.",
      "  --min-api                # Minimum Android API level compatibility.",
      "  --pg-conf <file>         # Proguard configuration <file>.",
      "  --pg-map-output <file>   # Output the resulting name and line mapping to <file>.",
      "  --no-tree-shaking        # Force disable tree shaking of unreachable classes.",
      "  --no-minification        # Force disable minification of names.",
      "  --no-desugaring          # Force disable desugaring.",
      "  --main-dex-rules <file>  # Proguard keep rules for classes to place in the",
      "                           # primary dex file.",
      "  --main-dex-list <file>   # List of classes to place in the primary dex file.",
      "  --main-dex-list-output <file>  # Output the full main-dex list in <file>.",
      "  --version                # Print the version of r8.",
      "  --help                   # Print this message."));

  private final ImmutableList<ProguardConfigurationRule> mainDexKeepRules;
  private final StringConsumer mainDexListConsumer;
  private final ProguardConfiguration proguardConfiguration;
  private final boolean enableTreeShaking;
  private final boolean enableMinification;
  private final boolean forceProguardCompatibility;
  private final StringConsumer proguardMapConsumer;
  private final Path proguardCompatibilityRulesOutput;

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
    return parse(args, origin, builder());
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
    return parse(args, origin, builder(handler));
  }

  private static Builder parse(String[] args, Origin origin, Builder builder) {
    ParseState state = new ParseState();
    parse(args, origin, builder, state);
    if (state.mode != null) {
      builder.setMode(state.mode);
    }
    Path outputPath = state.outputPath != null ? state.outputPath : Paths.get(".");
    OutputMode outputMode = state.outputMode != null ? state.outputMode : OutputMode.DexIndexed;
    builder.setOutput(outputPath, outputMode);
    return builder;
  }

  private static ParseState parse(
      String[] args,
      Origin argsOrigin,
      Builder builder,
      ParseState state) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--debug")) {
        if (state.mode == CompilationMode.RELEASE) {
          builder.getReporter().error(new StringDiagnostic(
              "Cannot compile in both --debug and --release mode.", argsOrigin));
        }
        state.mode = CompilationMode.DEBUG;
      } else if (arg.equals("--release")) {
        if (state.mode == CompilationMode.DEBUG) {
          builder.getReporter().error(new StringDiagnostic(
              "Cannot compile in both --debug and --release mode.", argsOrigin));
        }
        state.mode = CompilationMode.RELEASE;
      } else if (arg.equals("--dex")) {
        if (state.outputMode == OutputMode.ClassFile) {
          builder.getReporter().error(new StringDiagnostic(
              "Cannot compile in both --dex and --classfile output mode.", argsOrigin));
        }
        state.outputMode = OutputMode.DexIndexed;
      } else if (arg.equals("--classfile")) {
        if (state.outputMode == OutputMode.DexIndexed) {
          builder.getReporter().error(new StringDiagnostic(
              "Cannot compile in both --dex and --classfile output mode.", argsOrigin));
        }
        state.outputMode = OutputMode.ClassFile;
      } else if (arg.equals("--output")) {
        String outputPath = args[++i];
        if (state.outputPath != null) {
          builder.getReporter().error(new StringDiagnostic(
              "Cannot output both to '"
                  + state.outputPath.toString()
                  + "' and '"
                  + outputPath
                  + "'",
              argsOrigin));
        }
        state.outputPath = Paths.get(outputPath);
      } else if (arg.equals("--lib")) {
        builder.addLibraryFiles(Paths.get(args[++i]));
      } else if (arg.equals("--min-api")) {
        state.hasDefinedApiLevel =
            parseMinApi(builder, args[++i], state.hasDefinedApiLevel, argsOrigin);
      } else if (arg.equals("--no-tree-shaking")) {
        builder.setDisableTreeShaking(true);
      } else if (arg.equals("--no-minification")) {
        builder.setDisableMinification(true);
      } else if (arg.equals("--no-desugaring")) {
        builder.setDisableDesugaring(true);
      } else if (arg.equals("--main-dex-rules")) {
        builder.addMainDexRulesFiles(Paths.get(args[++i]));
      } else if (arg.equals("--main-dex-list")) {
        builder.addMainDexListFiles(Paths.get(args[++i]));
      } else if (arg.equals("--main-dex-list-output")) {
        builder.setMainDexListOutputPath(Paths.get(args[++i]));
      } else if (arg.equals("--optimize-multidex-for-linearalloc")) {
        builder.setOptimizeMultidexForLinearAlloc(true);
      } else if (arg.equals("--pg-conf")) {
        builder.addProguardConfigurationFiles(Paths.get(args[++i]));
      } else if (arg.equals("--pg-map-output")) {
        builder.setProguardMapOutputPath(Paths.get(args[++i]));
      } else if (arg.startsWith("@")) {
        // TODO(zerny): Replace this with pipe reading.
        Path argsFile = Paths.get(arg.substring(1));
        Origin argsFileOrigin = new PathOrigin(argsFile);
        try {
          List<String> linesInFile = FileUtils.readAllLines(argsFile);
          List<String> argsInFile = new ArrayList<>();
          for (String line : linesInFile) {
            for (String word : line.split("\\s")) {
              String trimmed = word.trim();
              if (!trimmed.isEmpty()) {
                argsInFile.add(trimmed);
              }
            }
          }
          // TODO(zerny): We need to define what CWD should be for files referenced in an args file.
          state = parse(argsInFile.toArray(new String[argsInFile.size()]),
              argsFileOrigin, builder, state);
        } catch (IOException e) {
          builder.getReporter().error(new StringDiagnostic(
              "Failed to read arguments from file " + argsFile + ": " + e.getMessage(),
              argsFileOrigin));
        }
      } else {
        if (arg.startsWith("--")) {
          builder.getReporter().error(new StringDiagnostic("Unknown option: " + arg, argsOrigin));
        }
        builder.addProgramFiles(Paths.get(arg));
      }
    }
    return state;
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
      boolean forceProguardCompatibility,
      StringConsumer proguardMapConsumer,
      Path proguardCompatibilityRulesOutput,
      boolean optimizeMultidexForLinearAlloc) {
    super(inputApp, mode, programConsumer, minApiLevel, reporter, enableDesugaring,
        optimizeMultidexForLinearAlloc);
    assert proguardConfiguration != null;
    assert mainDexKeepRules != null;
    this.mainDexKeepRules = mainDexKeepRules;
    this.mainDexListConsumer = mainDexListConsumer;
    this.proguardConfiguration = proguardConfiguration;
    this.enableTreeShaking = enableTreeShaking;
    this.enableMinification = enableMinification;
    this.forceProguardCompatibility = forceProguardCompatibility;
    this.proguardMapConsumer = proguardMapConsumer;
    this.proguardCompatibilityRulesOutput = proguardCompatibilityRulesOutput;
  }

  private R8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    mainDexKeepRules = ImmutableList.of();
    mainDexListConsumer = null;
    proguardConfiguration = null;
    enableTreeShaking = false;
    enableMinification = false;
    forceProguardCompatibility = false;
    proguardMapConsumer = null;
    proguardCompatibilityRulesOutput = null;
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
    internal.minimalMainDex = internal.debug;
    internal.mainDexListConsumer = mainDexListConsumer;
    internal.lineNumberOptimization =
        internal.debug || (forceProguardCompatibility && !proguardConfiguration.isOptimizing())
            ? LineNumberOptimization.OFF : LineNumberOptimization.ON;

    if (internal.debug) {
      // TODO(zerny): Should we support inlining in debug mode? b/62937285
      internal.enableInlining = false;
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

    internal.proguardCompatibilityRulesOutput = proguardCompatibilityRulesOutput;
    internal.dataResourceConsumer = internal.programConsumer.getDataResourceConsumer();

    // EXPERIMENTAL flags.
    assert !internal.forceProguardCompatibility;
    internal.forceProguardCompatibility = forceProguardCompatibility;

    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    return internal;
  }
}
