// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

/** Immutable command structure for an invocation of the {@link L8} libray compiler. */
@Keep
public final class L8Command extends BaseCompilerCommand {

  private static class DefaultL8DiagnosticsHandler implements DiagnosticsHandler {

    @Override
    public void error(Diagnostic error) {
      if (error instanceof DexFileOverflowDiagnostic) {
        DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
        DiagnosticsHandler.super.error(
            new StringDiagnostic(
                overflowDiagnostic.getDiagnosticMessage()
                    + ". Library too large. L8 can only produce a single .dex file"));
        return;
      }
      DiagnosticsHandler.super.error(error);
    }
  }

  /**
   * Builder for constructing a L8Command.
   *
   * <p>A builder is obtained by calling {@link L8Command#builder}.
   */
  @Keep
  public static class Builder extends BaseCompilerCommand.Builder<L8Command, Builder> {

    private Builder() {
      this(new DefaultL8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    /** Add dex program-data. */
    @Override
    public Builder addDexProgramData(byte[] data, Origin origin) {
      guard(() -> getAppBuilder().addDexProgramData(data, origin));
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
      if (getSpecialLibraryConfiguration() == null) {
        reporter.error("L8 requires a special library configuration");
      } else if (!getSpecialLibraryConfiguration().equals("default")) {
        reporter.error("L8 currently require special library configuration to be \"default\"");
      }
      if (getProgramConsumer() instanceof ClassFileConsumer) {
        reporter.error("L8 does not support compiling to Java class files");
      }
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("L8 does not support compiling to dex per class");
      }
      if (getAppBuilder().hasMainDexList()) {
        reporter.error("L8 does not support a main dex list");
      } else if (getMainDexListConsumer() != null) {
        reporter.error("L8 does not support generating a main dex list");
      }
      super.validate();
    }

    @Override
    L8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new L8Command(isPrintHelp(), isPrintVersion());
      }

      return new L8Command(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMainDexListConsumer(),
          getMinApiLevel(),
          getReporter(),
          getSpecialLibraryConfiguration());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  private L8Command(
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      StringConsumer mainDexListConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      String specialLibraryConfiguration) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        diagnosticsHandler,
        true,
        false,
        specialLibraryConfiguration);
  }

  private L8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
  }

  private Map<String, String> buildBackportCoreLibraryMembers() {
    // R8 specific to deal with *8 removal.
    return ImmutableMap.<String, String>builder()
        .put("java.lang.Double8", "java.lang.Double")
        .put("java.lang.Integer8", "java.lang.Integer")
        .put("java.lang.Long8", "java.lang.Long")
        .build();
  }

  private Map<String, String> buildRetargetCoreLibraryMemberForCoreLibCompilation() {
    /*
      --retarget_core_library_member.

      The bazel configuration only have this single --retarget_core_library_member option:

      --retarget_core_library_member \
          "java/util/LinkedHashSet#spliterator->java/util/DesugarLinkedHashSet"

      The configuration below is the full configuration for programs using the desugared library.
      This should fine, as any calls to these re-targeted methods should be rewritten. The main
      reason for adding all of them is that the additional files added to the desugared library
      for running some of the JDK 11 tests use these APIs.
    */
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

  private List<String> buildDontRewriteInvocations() {
    // --dont_rewrite_core_library_invocation "java/util/Iterator#remove".
    return ImmutableList.of("java.util.Iterator#remove");
  }

  private Map<String, String> buildPrefixRewritingForCoreLibCompilation() {
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        // Extra flags for R8
        .put("java.io.DesugarBufferedReader", "j$.io.DesugarBufferedReader")
        .put("java.io.UncheckedIOException", "j$.io.UncheckedIOException")
        // Bazel flags.
        .put("java.lang.Double8", "j$.lang.Double8")
        .put("java.lang.Integer8", "j$.lang.Integer8")
        .put("java.lang.Long8", "j$.lang.Long8")
        .put("java.lang.Math8", "j$.lang.Math8")
        .put("java.time.", "j$.time.")
        .put("java.util.stream.", "j$.util.stream.")
        .put("java.util.function.", "j$.util.function.")
        .put("java.util.Comparators", "j$.util.Comparators")
        .put("java.util.Desugar", "j$.util.Desugar")
        .put("java.util.DoubleSummaryStatistics", "j$.util.DoubleSummaryStatistics")
        .put("java.util.IntSummaryStatistics", "j$.util.IntSummaryStatistics")
        .put("java.util.LongSummaryStatistics", "j$.util.LongSummaryStatistics")
        .put("java.util.Objects", "j$.util.Objects")
        .put("java.util.Optional", "j$.util.Optional")
        .put("java.util.PrimitiveIterator", "j$.util.PrimitiveIterator")
        .put("java.util.SortedSet$1", "j$.util.SortedSet$1")
        .put("java.util.Spliterator", "j$.util.Spliterator")
        .put("java.util.StringJoiner", "j$.util.StringJoiner")
        .put("java.util.Tripwire", "j$.util.Tripwire")
        .put("java.util.concurrent.ConcurrentHashMap", "j$.util.concurrent.ConcurrentHashMap")
        .put("java.util.concurrent.DesugarUnsafe", "j$.util.concurrent.DesugarUnsafe")
        .put("java.util.concurrent.ThreadLocalRandom", "j$.util.concurrent.ThreadLocalRandom")
        .put("java.util.concurrent.atomic.DesugarAtomic", "j$.util.concurrent.atomic.DesugarAtomic")
        .build();
  }

  private Map<String, String> buildEmulateLibraryInterface() {
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
    options.coreLibraryCompilation = true;
    options.backportCoreLibraryMembers = buildBackportCoreLibraryMembers();
    options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForCoreLibCompilation();
    options.dontRewriteInvocations = buildDontRewriteInvocations();
    options.rewritePrefix = buildPrefixRewritingForCoreLibCompilation();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(new DexItemFactory(), getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    assert internal.mainDexListConsumer == null;
    assert !internal.minimalMainDex;
    internal.minApiLevel = getMinApiLevel();
    assert !internal.intermediate;
    assert internal.readCompileTimeAnnotations;

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;

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
    assert internal.enableDesugaring;
    assert internal.enableInheritanceClassInDexDistributor;
    internal.enableInheritanceClassInDexDistributor = false;

    // TODO(134732760): This is still work in progress.
    assert internal.rewritePrefix.isEmpty();
    assert internal.emulateLibraryInterface.isEmpty();
    assert internal.retargetCoreLibMember.isEmpty();
    assert internal.backportCoreLibraryMembers.isEmpty();
    assert internal.dontRewriteInvocations.isEmpty();
    assert getSpecialLibraryConfiguration().equals("default");
    configureLibraryDesugaring(internal);

    return internal;
  }
}
