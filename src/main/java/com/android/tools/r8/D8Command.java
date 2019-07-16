// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

  private static class ClasspathInputOrigin extends InputFileOrigin {

    public ClasspathInputOrigin(Path file) {
      super("classpath input", file);
    }
  }

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

    private Builder() {
      this(new DefaultD8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    private Builder(AndroidApp app) {
      super(app);
    }

    /** Add dex program-data. */
    @Override
    public Builder addDexProgramData(byte[] data, Origin origin) {
      guard(() -> getAppBuilder().addDexProgramData(data, origin));
      return self();
    }

    /** Add classpath file resources. These have @Override to ensure binary compatibility. */
    @Override
    public Builder addClasspathFiles(Path... files) {
      return super.addClasspathFiles(files);
    }

    /** Add classpath file resources. */
    @Override
    public Builder addClasspathFiles(Collection<Path> files) {
      return super.addClasspathFiles(files);
    }

    /** Add classfile resources provider for class-path resources. */
    @Override
    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      return super.addClasspathResourceProvider(provider);
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

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.DEBUG;
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (getProgramConsumer() instanceof ClassFileConsumer) {
        reporter.error("D8 does not support compiling to Java class files");
      }
      if (getAppBuilder().hasMainDexList()) {
        if (intermediate) {
          reporter.error("Option --main-dex-list cannot be used with --intermediate");
        }
        if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
          reporter.error("Option --main-dex-list cannot be used with --file-per-class");
        }
      } else if (getMainDexListConsumer() != null) {
        reporter.error("Option --main-dex-list-output require --main-dex-list");
      }
      if (getMinApiLevel() >= AndroidApiLevel.L.getLevel()) {
        if (getMainDexListConsumer() != null || getAppBuilder().hasMainDexList()) {
          reporter.error(
              "D8 does not support main-dex inputs and outputs when compiling to API level "
                  + AndroidApiLevel.L.getLevel()
                  + " and above");
        }
      }
      if (getSpecialLibraryConfiguration() != null
          && !getSpecialLibraryConfiguration().equals("default")) {
        reporter.error("D8 currently require special library configuration to be \"default\"");
      }
      super.validate();
    }

    @Override
    D8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new D8Command(isPrintHelp(), isPrintVersion());
      }

      intermediate |= getProgramConsumer() instanceof DexFilePerClassFileConsumer;

      return new D8Command(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMainDexListConsumer(),
          getMinApiLevel(),
          getReporter(),
          !getDisableDesugaring(),
          intermediate,
          isOptimizeMultidexForLinearAlloc(),
          getSpecialLibraryConfiguration());
    }
  }

  static final String USAGE_MESSAGE = D8CommandParser.USAGE_MESSAGE;

  private boolean intermediate = false;

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
      boolean enableDesugaring,
      boolean intermediate,
      boolean optimizeMultidexForLinearAlloc,
      String specialLibraryConfiguration) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        diagnosticsHandler,
        enableDesugaring,
        optimizeMultidexForLinearAlloc,
        specialLibraryConfiguration);
    this.intermediate = intermediate;
  }

  private D8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
  }

  private Map<String, String> buildPrefixRewritingForProgramCompilation() {
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        // Following flags are ignored (already desugared).
        // .put("java.lang.Double8", "j$.lang.Double8")
        // .put("java.lang.Integer8", "j$.lang.Integer8")
        // .put("java.lang.Long8", "j$.lang.Long8")
        // .put("java.lang.Math8", "j$.lang.Math8")
        .put("java.time.", "j$.time.")
        .put("java.util.stream.", "j$.util.stream.")
        .put("java.util.function.", "j$.util.function.")
        .put("java.util.Desugar", "j$.util.Desugar")
        .put("java.util.DoubleSummaryStatistics", "j$.util.DoubleSummaryStatistics")
        .put("java.util.IntSummaryStatistics", "j$.util.IntSummaryStatistics")
        .put("java.util.LongSummaryStatistics", "j$.util.LongSummaryStatistics")
        .put("java.util.Objects", "j$.util.Objects")
        .put("java.util.Optional", "j$.util.Optional")
        .put("java.util.PrimitiveIterator", "j$.util.PrimitiveIterator")
        .put("java.util.Spliterator", "j$.util.Spliterator")
        .put("java.util.StringJoiner", "j$.util.StringJoiner")
        .put("java.util.concurrent.ConcurrentHashMap", "j$.util.concurrent.ConcurrentHashMap")
        .put("java.util.concurrent.ThreadLocalRandom", "j$.util.concurrent.ThreadLocalRandom")
        .put("java.util.concurrent.atomic.DesugarAtomic", "j$.util.concurrent.atomic.DesugarAtomic")
        .build();
  }

  protected Map<String, String> buildRetargetCoreLibraryMemberForProgramCompilation() {
    // --retarget_core_library_member.
    return ImmutableMap.<String, String>builder()
        // We ignore the following flags required by Bazel because desugaring of these methods
        // is done separately.
        // .put("java.lang.Double#max", "java.lang.Double8")
        // .put("java.lang.Double#min", "java.lang.Double8")
        // .put("java.lang.Double#sum", "java.lang.Double8")
        // .put("java.lang.Integer#max", "java.lang.Integer8")
        // .put("java.lang.Integer#min", "java.lang.Integer8")
        // .put("java.lang.Integer#sum", "java.lang.Integer8")
        // .put("java.lang.Long#max", "java.lang.Long")
        // .put("java.lang.Long#min", "java.lang.Long")
        // .put("java.lang.Long#sum", "java.lang.Long")
        // .put("java.lang.Math#toIntExact", "java.lang.Math8")
        .put("java.util.Arrays#stream", "java.util.DesugarArrays")
        .put("java.util.Arrays#spliterator", "java.util.DesugarArrays")
        .put("java.util.Calendar#toInstant", "java.util.DesugarCalendar")
        .put("java.util.Date#from", "java.util.DesugarDate")
        .put("java.util.Date#toInstant", "java.util.DesugarDate")
        .put("java.util.GregorianCalendar#from", "java.util.DesugarGregorianCalendar")
        .put("java.util.GregorianCalendar#toZonedDateTime", "java.util.DesugarGregorianCalendar")
        .put("java.util.LinkedHashSet#spliterator", "java.util.DesugarLinkedHashSet")
        .put(
            "java.util.concurrent.atomic.AtomicInteger#getAndUpdate",
            "java.util.concurrent.atomic.DesugarAtomicInteger")
        .put(
            "java.util.concurrent.atomic.AtomicInteger#updateAndGet",
            "java.util.concurrent.atomic.DesugarAtomicInteger")
        .put(
            "java.util.concurrent.atomic.AtomicInteger#getAndAccumulate",
            "java.util.concurrent.atomic.DesugarAtomicInteger")
        .put(
            "java.util.concurrent.atomic.AtomicInteger#accumulateAndGet",
            "java.util.concurrent.atomic.DesugarAtomicInteger")
        .put(
            "java.util.concurrent.atomic.AtomicLong#getAndUpdate",
            "java.util.concurrent.atomic.DesugarAtomicLong")
        .put(
            "java.util.concurrent.atomic.AtomicLong#updateAndGet",
            "java.util.concurrent.atomic.DesugarAtomicLong")
        .put(
            "java.util.concurrent.atomic.AtomicLong#getAndAccumulate",
            "java.util.concurrent.atomic.DesugarAtomicLong")
        .put(
            "java.util.concurrent.atomic.AtomicLong#accumulateAndGet",
            "java.util.concurrent.atomic.DesugarAtomicLong")
        .put(
            "java.util.concurrent.atomic.AtomicReference#getAndUpdate",
            "java.util.concurrent.atomic.DesugarAtomicReference")
        .put(
            "java.util.concurrent.atomic.AtomicReference#updateAndGet",
            "java.util.concurrent.atomic.DesugarAtomicReference")
        .put(
            "java.util.concurrent.atomic.AtomicReference#getAndAccumulate",
            "java.util.concurrent.atomic.DesugarAtomicReference")
        .put(
            "java.util.concurrent.atomic.AtomicReference#accumulateAndGet",
            "java.util.concurrent.atomic.DesugarAtomicReference")
        .build();
  }

  protected List<String> buildDontRewriteInvocations() {
    // --dont_rewrite_core_library_invocation "java/util/Iterator#remove".
    return ImmutableList.of("java.util.Iterator#remove");
  }

  protected Map<String, String> buildEmulateLibraryInterface() {
    return ImmutableMap.<String, String>builder()
        // --emulate_core_library_interface.
        // Bazel flags.
        .put("java.util.Map$Entry", "j$.util.Map$Entry")
        .put("java.util.Collection", "j$.util.Collection")
        .put("java.util.Map", "j$.util.Map")
        .put("java.util.Iterator", "j$.util.Iterator")
        .put("java.util.Comparator", "j$.util.Comparator")
        // Extra flags: in R8 we marked as emulated all interfaces
        // with default methods. Emulated interfaces have their
        // companion class moved to j$ and have a dispatch class.
        // Bazel instead analyzes the class hierarchy.
        .put(
            "java.util.concurrent.ConcurrentNavigableMap",
            "j$.util.concurrent.ConcurrentNavigableMap")
        .put("java.util.List", "j$.util.List")
        .put("java.util.SortedSet", "j$.util.SortedSet")
        .put("java.util.Set", "j$.util.Set")
        .put("java.util.concurrent.ConcurrentMap", "j$.util.concurrent.ConcurrentMap")
        .build();
  }

  private void configureLibraryDesugaring(InternalOptions options) {
    options.coreLibraryCompilation = false;
    options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForProgramCompilation();
    options.dontRewriteInvocations = buildDontRewriteInvocations();
    options.rewritePrefix = buildPrefixRewritingForProgramCompilation();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(new DexItemFactory(), getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    internal.mainDexListConsumer = getMainDexListConsumer();
    internal.minimalMainDex = internal.debug;
    internal.minApiLevel = getMinApiLevel();
    internal.intermediate = intermediate;
    internal.readCompileTimeAnnotations = intermediate;

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;
    internal.passthroughDexCode = true;

    // Assert some of R8 optimizations are disabled.
    assert !internal.enableDynamicTypeOptimization;
    assert !internal.enableInlining;
    assert !internal.enableClassInlining;
    assert !internal.enableHorizontalClassMerging;
    assert !internal.enableVerticalClassMerging;
    assert !internal.enableClassStaticizer;
    assert !internal.enableEnumValueOptimization;
    assert !internal.outline.enabled;
    assert !internal.enableValuePropagation;
    assert !internal.enableLambdaMerging;
    assert !internal.enableTreeShakingOfLibraryMethodOverrides;

    // TODO(b/137168535) Disable non-null tracking for now.
    internal.enableNonNullTracking = false;
    internal.enableDesugaring = getEnableDesugaring();
    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    // TODO(134732760): This is still work in progress.
    assert internal.rewritePrefix.isEmpty();
    assert internal.emulateLibraryInterface.isEmpty();
    assert internal.retargetCoreLibMember.isEmpty();
    assert internal.backportCoreLibraryMembers.isEmpty();
    assert internal.dontRewriteInvocations.isEmpty();
    if (getSpecialLibraryConfiguration() != null) {
      configureLibraryDesugaring(internal);
    }

    return internal;
  }
}
