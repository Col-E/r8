// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.origin.StandardOutOrigin;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IOExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Immutable command structure for an invocation of the {@link D8} compiler.
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

    private Builder() {
      setMode(CompilationMode.RELEASE);
    }

    // Internal compatibility mode for use from CompatProguard tool.
    Builder(boolean forceProguardCompatibility) {
      setMode(CompilationMode.RELEASE);
      this.forceProguardCompatibility = forceProguardCompatibility;
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
      setMode(CompilationMode.DEBUG);
    }

    private Builder(AndroidApp app) {
      super(app);
      setMode(CompilationMode.RELEASE);
    }

    private Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(app, diagnosticsHandler);
      setMode(CompilationMode.RELEASE);
    }

    @Override
    Builder self() {
      return this;
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

    /** Unsupported API. Will throw on any usage. */
    @Override
    @Deprecated
    public Builder setOutputMode(OutputMode outputMode) {
      throw new CompilationError("Invalid API use for R8");
    }

    /** Unsupported API. Will throw on any usage. */
    @Override
    @Deprecated
    public Builder setOutputPath(Path outputPath) {
      throw new CompilationError("Invalid API use for R8");
    }

    @Override
    public Builder addProgramResourceProvider(ProgramResourceProvider programProvider) {
      return super.addProgramResourceProvider(
          new EnsureNonDexProgramResourceProvider(programProvider));
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      // TODO(b/70656566): Move up super once the deprecated API is removed.
      if (getProgramConsumer() == null) {
        // This is never the case for a command-line parse, so we report using API references.
        reporter.error("A ProgramConsumer or Output is required for compilation");
      }
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
      super.validate();
    }

    @Override
    R8Command makeCommand() {
      try {
        // If printing versions ignore everything else.
        if (isPrintHelp() || isPrintVersion()) {
          return new R8Command(isPrintHelp(), isPrintVersion());
        }

        return makeR8Command();
      } catch (IOException e) {
        throw getReporter().fatalError(new IOExceptionDiagnostic(e), e);
      } catch (CompilationException e) {
        throw getReporter().fatalError(new StringDiagnostic(e.getMessage()), e);
      }
    }

    private R8Command makeR8Command()
        throws IOException, CompilationException {
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

      ProguardConfiguration.Builder configurationBuilder;
      if (proguardConfigs.isEmpty()) {
        configurationBuilder = ProguardConfiguration.builder(factory, reporter);
      } else {
        ProguardConfigurationParser parser =
            new ProguardConfigurationParser(factory, reporter);
        parser.parse(proguardConfigs);
        configurationBuilder = parser.getConfigurationBuilder();
        configurationBuilder.setForceProguardCompatibility(forceProguardCompatibility);
      }

      if (proguardConfigurationConsumer != null) {
        proguardConfigurationConsumer.accept(configurationBuilder);
      }
      ProguardConfiguration configuration = configurationBuilder.build();
      getAppBuilder()
          .addFilteredProgramArchives(configuration.getInjars())
          .addFilteredLibraryArchives(configuration.getLibraryjars());

      boolean useTreeShaking = !disableTreeShaking && configuration.isShrinking();
      boolean useMinification = !disableMinification && configuration.isObfuscating();

      assert getProgramConsumer() != null;

      R8Command command =
          new R8Command(
              getAppBuilder().build(),
              getProgramConsumer(),
              null,
              mainDexKeepRules,
              mainDexListConsumer,
              configuration,
              getMode(),
              getMinApiLevel(),
              reporter,
              getEnableDesugaring(),
              useTreeShaking,
              useMinification,
              forceProguardCompatibility,
              proguardMapConsumer,
              proguardCompatibilityRulesOutput);

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
  }

  // Internal state to verify parsing properties not enforced by the builder.
  private static class ParseState {
    CompilationMode mode = null;
    OutputMode outputMode = null;
    Path outputPath = null;
  }

  static final String USAGE_MESSAGE = String.join("\n", ImmutableList.of(
      "Usage: r8 [options] <input-files>",
      " where <input-files> are any combination of dex, class, zip, jar, or apk files",
      " and options are:",
      "  --release                # Compile without debugging information (default).",
      "  --debug                  # Compile with debugging information.",
      "  --dex                    # Compile program to DEX file format (default).",
      "  --classfile              # Compile program to Java classfile format.",
      "  --output <file>          # Output result in <file>.",
      "                           # <file> must be an existing directory or a zip file.",
      "  --lib <file>             # Add <file> as a library resource.",
      "  --min-api                # Minimum Android API level compatibility.",
      "  --pg-conf <file>         # Proguard configuration <file>.",
      "  --pg-map-output <file>   # Output the resulting name and line mapping to <file>.",
      "  --no-tree-shaking        # Force disable tree shaking of unreachable classes.",
      "  --no-minification        # Force disable minification of names.",
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
    Builder builder = builder();
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
        builder.setMinApiLevel(Integer.valueOf(args[++i]));
      } else if (arg.equals("--no-tree-shaking")) {
        builder.setDisableTreeShaking(true);
      } else if (arg.equals("--no-minification")) {
        builder.setDisableMinification(true);
      } else if (arg.equals("--main-dex-rules")) {
        builder.addMainDexRulesFiles(Paths.get(args[++i]));
      } else if (arg.equals("--main-dex-list")) {
        builder.addMainDexListFiles(Paths.get(args[++i]));
      } else if (arg.equals("--main-dex-list-output")) {
        builder.setMainDexListOutputPath(Paths.get(args[++i]));
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

  private
  R8Command(
      AndroidApp inputApp,
      ProgramConsumer programConsumer,
      OutputOptions outputOptions,
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
      Path proguardCompatibilityRulesOutput) {
    super(inputApp, mode, programConsumer, outputOptions, minApiLevel, reporter, enableDesugaring);
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
    // -dontoptimize disables optimizations by flipping related flags.
    if (!proguardConfiguration.isOptimizing()) {
      internal.skipClassMerging = true;
      internal.inlineAccessors = false;
      internal.removeSwitchMaps = false;
      internal.outline.enabled = false;
      internal.propagateMemberValue = false;
    }
    assert !internal.skipMinification;
    internal.skipMinification = !getEnableMinification();
    assert internal.useTreeShaking;
    internal.useTreeShaking = getEnableTreeShaking();
    assert !internal.ignoreMissingClasses;
    internal.ignoreMissingClasses = proguardConfiguration.isIgnoreWarnings()
        // TODO(70706667): We probably only want this in Proguard compatibility mode.
        || (forceProguardCompatibility
            && !proguardConfiguration.isOptimizing()
            && internal.skipMinification
            && !internal.useTreeShaking);

    assert !internal.verbose;
    internal.mainDexKeepRules = mainDexKeepRules;
    internal.minimalMainDex = internal.debug;
    internal.mainDexListConsumer = mainDexListConsumer;
    internal.lineNumberOptimization =
        internal.debug || (forceProguardCompatibility && !proguardConfiguration.isOptimizing())
            ? LineNumberOptimization.OFF : LineNumberOptimization.ON;

    if (internal.debug) {
      // TODO(zerny): Should we support removeSwitchMaps in debug mode? b/62936642
      internal.removeSwitchMaps = false;
      // TODO(zerny): Should we support inlining in debug mode? b/62937285
      internal.inlineAccessors = false;
      // TODO(zerny): Should we support outlining in debug mode? b/62937285
      internal.outline.enabled = false;
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

    // EXPERIMENTAL flags.
    assert !internal.forceProguardCompatibility;
    internal.forceProguardCompatibility = forceProguardCompatibility;

    return internal;
  }
}
