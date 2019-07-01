// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CoreLibDesugarTestBase extends TestBase {

  private Map<String, String> buildPrefixRewritingForCoreLibCompilation() {
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        // Extra flags for R8
        .put("java.io.DesugarBufferedReader", "j$.io.DesugarBufferedReader")
        .put("java.io.UncheckedIOException", "j$.io.UncheckedIOException")
        // TODO (b/134732760): Rewrite java.lang.*8 to java.lang.*
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

  private Map<String, String> buildPrefixRewritingForProgramCompilation() {
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        // TODO (b/134732760): Rewrite java.lang.*8 to java.lang.*
        .put("java.lang.Double8", "j$.lang.Double8")
        .put("java.lang.Integer8", "j$.lang.Integer8")
        .put("java.lang.Long8", "j$.lang.Long8")
        .put("java.lang.Math8", "j$.lang.Math8")
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

  private Map<String, String> buildRetargetCoreLibraryMemberForCoreLibCompilation() {
    // --retarget_core_library_member.
    return ImmutableMap.of("java/util/LinkedHashSet#spliterator", "j$/util/DesugarLinkedHashSet");
  }

  private Map<String, String> buildRetargetCoreLibraryMemberForProgramCompilation() {
    // --retarget_core_library_member.
    return ImmutableMap.<String, String>builder()
        // We ignore the following flags required by Bazel because desugaring of these methods
        // is done separately.
        // .put("java/lang/Double#max", "j$/lang/Double8")
        // .put("java/lang/Double#min", "j$/lang/Double8")
        // .put("java/lang/Double#sum", "j$/lang/Double8")
        // .put("java/lang/Integer#max", "j$/lang/Integer8")
        // .put("java/lang/Integer#min", "j$/lang/Integer8")
        // .put("java/lang/Integer#sum", "j$/lang/Integer8")
        // .put("java/lang/Long#max", "j$/lang/Long")
        // .put("java/lang/Long#min", "j$/lang/Long")
        // .put("java/lang/Long#sum", "j$/lang/Long")
        // .put("java/lang/Math#toIntExact", "j$/lang/Math8")
        .put("java/util/Arrays#stream", "j$/util/DesugarArrays")
        .put("java/util/Arrays#spliterator", "j$/util/DesugarArrays")
        .put("java/util/Calendar#toInstant", "j$/util/DesugarCalendar")
        .put("java/util/Date#from", "j$/util/DesugarDate")
        .put("java/util/Date#toInstant", "j$/util/DesugarDate")
        .put("java/util/GregorianCalendar#from", "j$/util/DesugarGregorianCalendar")
        .put("java/util/GregorianCalendar#toZonedDateTime", "j$/util/DesugarGregorianCalendar")
        .put("java/util/LinkedHashSet#spliterator", "j$/util/DesugarLinkedHashSet")
        .put(
            "java/util/concurrent/atomic/AtomicInteger#getAndUpdate",
            "j$/util/concurrent/atomic/DesugarAtomicInteger")
        .put(
            "java/util/concurrent/atomic/AtomicInteger#updateAndGet",
            "j$/util/concurrent/atomic/DesugarAtomicInteger")
        .put(
            "java/util/concurrent/atomic/AtomicInteger#getAndAccumulate",
            "j$/util/concurrent/atomic/DesugarAtomicInteger")
        .put(
            "java/util/concurrent/atomic/AtomicInteger#accumulateAndGet",
            "j$/util/concurrent/atomic/DesugarAtomicInteger")
        .put(
            "java/util/concurrent/atomic/AtomicLong#getAndUpdate",
            "j$/util/concurrent/atomic/DesugarAtomicLong")
        .put(
            "java/util/concurrent/atomic/AtomicLong#updateAndGet",
            "j$/util/concurrent/atomic/DesugarAtomicLong")
        .put(
            "java/util/concurrent/atomic/AtomicLong#getAndAccumulate",
            "j$/util/concurrent/atomic/DesugarAtomicLong")
        .put(
            "java/util/concurrent/atomic/AtomicLong#accumulateAndGet",
            "j$/util/concurrent/atomic/DesugarAtomicLong")
        .put(
            "java/util/concurrent/atomic/AtomicReference#getAndUpdate",
            "j$/util/concurrent/atomic/DesugarAtomicReference")
        .put(
            "java/util/concurrent/atomic/AtomicReference#updateAndGet",
            "j$/util/concurrent/atomic/DesugarAtomicReference")
        .put(
            "java/util/concurrent/atomic/AtomicReference#getAndAccumulate",
            "j$/util/concurrent/atomic/DesugarAtomicReference")
        .put(
            "java/util/concurrent/atomic/AtomicReference#accumulateAndGet",
            "j$/util/concurrent/atomic/DesugarAtomicReference")
        .build();
  }

  private List<String> buildDontRewriteInvocations() {
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

  protected boolean requiresCoreLibDesugaring(TestParameters parameters) {
    return parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        < AndroidApiLevel.N.getLevel();
  }

  protected void configureCoreLibDesugarForCoreLibCompilation(InternalOptions options) {
    options.coreLibraryCompilation = true;
    options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForCoreLibCompilation();
    options.dontRewriteInvocations = buildDontRewriteInvocations();
    options.rewritePrefix = buildPrefixRewritingForCoreLibCompilation();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }

  protected void configureCoreLibDesugarForProgramCompilation(InternalOptions options) {
    options.coreLibraryCompilation = false;
    options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForProgramCompilation();
    options.dontRewriteInvocations = buildDontRewriteInvocations();
    options.rewritePrefix = buildPrefixRewritingForProgramCompilation();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) throws Exception {
    return testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(ToolHelper.getDesugarJDKLibs())
        .addOptionsModification(this::configureCoreLibDesugarForCoreLibCompilation)
        .setMinApi(apiLevel)
        .compile()
        .writeToZip();
  }

  protected Path buildDesugaredLibrary(TestRuntime runtime) throws Exception {
    return buildDesugaredLibrary(runtime.asDex().getMinApiLevel());
  }
}
