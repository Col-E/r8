// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.InternalOptions.DETERMINISTIC_DEBUGGING;

import com.android.tools.r8.AssertionsConfiguration.AssertionTransformation;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
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
@Keep
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
  @Keep
  public static class Builder extends BaseCompilerCommand.Builder<D8Command, Builder> {

    private boolean intermediate = false;
    private DesugarGraphConsumer desugarGraphConsumer = null;
    private StringConsumer desugaredLibraryKeepRuleConsumer = null;
    private String synthesizedClassPrefix = "";
    private boolean enableMainDexListCheck = true;
    private boolean minimalMainDex = false;
    private boolean skipDump = false;
    private final List<ProguardConfigurationSource> mainDexRules = new ArrayList<>();

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

    /** Set input proguard map used for distribution of classes in multi-dex. */
    public Builder setProguardInputMapFile(Path proguardInputMap) {
      getAppBuilder().setProguardMapInputData(proguardInputMap);
      return self();
    }

    /**
     * Indicate if compilation is to intermediate results, i.e., intended for later merging.
     *
     * <p>Intermediate mode is implied if compiling results to a "file-per-class-file".
     */
    public Builder setIntermediate(boolean value) {
      this.intermediate = value;
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

    /**
     * Allow to skip to dump into file and dump into directory instruction, this is primarily used
     * for chained compilation in L8 so there are no duplicated dumps.
     */
    Builder skipDump() {
      skipDump = true;
      return self();
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
      super.validate();
    }

    @Override
    D8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new D8Command(isPrintHelp(), isPrintVersion());
      }

      intermediate |= getProgramConsumer() instanceof DexFilePerClassFileConsumer;

      DexItemFactory factory = new DexItemFactory();
      LegacyDesugaredLibrarySpecification desugaredLibrarySpecification =
          getDesugaredLibraryConfiguration(factory, false);

      ImmutableList<ProguardConfigurationRule> mainDexKeepRules =
          ProguardConfigurationParser.parse(mainDexRules, factory, getReporter());

      return new D8Command(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMainDexListConsumer(),
          getMinApiLevel(),
          getReporter(),
          getDesugaringState(),
          intermediate,
          isOptimizeMultidexForLinearAlloc(),
          getIncludeClassesChecksum(),
          getDexClassChecksumFilter(),
          getDesugarGraphConsumer(),
          desugaredLibraryKeepRuleConsumer,
          desugaredLibrarySpecification,
          getAssertionsConfiguration(),
          getOutputInspections(),
          synthesizedClassPrefix,
          skipDump,
          enableMainDexListCheck,
          minimalMainDex,
          mainDexKeepRules,
          getThreadCount(),
          getDumpInputFlags(),
          getMapIdProvider(),
          factory);
    }
  }

  static final String USAGE_MESSAGE = D8CommandParser.USAGE_MESSAGE;

  private final boolean intermediate;
  private final DesugarGraphConsumer desugarGraphConsumer;
  private final StringConsumer desugaredLibraryKeepRuleConsumer;
  private final LegacyDesugaredLibrarySpecification desugaredLibrarySpecification;
  private final String synthesizedClassPrefix;
  private final boolean skipDump;
  private final boolean enableMainDexListCheck;
  private final boolean minimalMainDex;
  private final ImmutableList<ProguardConfigurationRule> mainDexKeepRules;
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

  private D8Command(
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      StringConsumer mainDexListConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      DesugarState enableDesugaring,
      boolean intermediate,
      boolean optimizeMultidexForLinearAlloc,
      boolean encodeChecksum,
      BiPredicate<String, Long> dexClassChecksumFilter,
      DesugarGraphConsumer desugarGraphConsumer,
      StringConsumer desugaredLibraryKeepRuleConsumer,
      LegacyDesugaredLibrarySpecification desugaredLibrarySpecification,
      List<AssertionsConfiguration> assertionsConfiguration,
      List<Consumer<Inspector>> outputInspections,
      String synthesizedClassPrefix,
      boolean skipDump,
      boolean enableMainDexListCheck,
      boolean minimalMainDex,
      ImmutableList<ProguardConfigurationRule> mainDexKeepRules,
      int threadCount,
      DumpInputFlags dumpInputFlags,
      MapIdProvider mapIdProvider,
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
        null);
    this.intermediate = intermediate;
    this.desugarGraphConsumer = desugarGraphConsumer;
    this.desugaredLibraryKeepRuleConsumer = desugaredLibraryKeepRuleConsumer;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.synthesizedClassPrefix = synthesizedClassPrefix;
    this.skipDump = skipDump;
    this.enableMainDexListCheck = enableMainDexListCheck;
    this.minimalMainDex = minimalMainDex;
    this.mainDexKeepRules = mainDexKeepRules;
    this.factory = factory;
  }

  private D8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    intermediate = false;
    desugarGraphConsumer = null;
    desugaredLibraryKeepRuleConsumer = null;
    desugaredLibrarySpecification = null;
    synthesizedClassPrefix = null;
    skipDump = false;
    enableMainDexListCheck = true;
    minimalMainDex = false;
    mainDexKeepRules = null;
    factory = null;
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    if (internal.isGeneratingClassFiles()) {
      internal.cfToCfDesugar = true;
      // Turn off switch optimizations when desugaring to class file format.
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
    internal.desugarGraphConsumer = desugarGraphConsumer;
    internal.mainDexKeepRules = mainDexKeepRules;
    internal.lineNumberOptimization = LineNumberOptimization.OFF;

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;
    internal.passthroughDexCode = true;
    assert internal.neverMergePrefixes.contains("j$.");

    // Assert some of R8 optimizations are disabled.
    assert !internal.inlinerOptions().enableInlining;
    assert !internal.enableClassInlining;
    assert !internal.enableVerticalClassMerging;
    assert !internal.enableEnumValueOptimization;
    assert !internal.outline.enabled;
    assert !internal.enableTreeShakingOfLibraryMethodOverrides;

    // TODO(b/187675788): Enable class merging for synthetics in D8.
    HorizontalClassMergerOptions horizontalClassMergerOptions =
        internal.horizontalClassMergerOptions();
    horizontalClassMergerOptions.disable();
    assert !horizontalClassMergerOptions.isEnabled(HorizontalClassMerger.Mode.INITIAL);
    assert !horizontalClassMergerOptions.isEnabled(HorizontalClassMerger.Mode.FINAL);

    internal.desugarState = getDesugarState();
    internal.encodeChecksums = getIncludeClassesChecksum();
    internal.dexClassChecksumFilter = getDexClassChecksumFilter();
    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    internal.desugaredLibrarySpecification = desugaredLibrarySpecification;
    internal.synthesizedClassPrefix = synthesizedClassPrefix;
    internal.desugaredLibraryKeepRuleConsumer = desugaredLibraryKeepRuleConsumer;

    // Default is to remove all javac generated assertion code when generating dex.
    assert internal.assertionsConfiguration == null;
    internal.assertionsConfiguration =
        new AssertionConfigurationWithDefault(
            AssertionTransformation.DISABLE, getAssertionsConfiguration());

    internal.outputInspections = InspectorImpl.wrapInspections(getOutputInspections());

    if (!DETERMINISTIC_DEBUGGING) {
      assert internal.threadCount == ThreadUtils.NOT_SPECIFIED;
      internal.threadCount = getThreadCount();
    }

    internal.setDumpInputFlags(getDumpInputFlags(), skipDump);
    internal.dumpOptions = dumpOptions();

    return internal;
  }

  private DumpOptions dumpOptions() {
    DumpOptions.Builder builder = DumpOptions.builder(Tool.D8);
    dumpBaseCommandOptions(builder);
    return builder
        .setIntermediate(intermediate)
        .setDesugaredLibraryConfiguration(desugaredLibrarySpecification)
        .setMainDexKeepRules(mainDexKeepRules)
        .build();
  }
}
