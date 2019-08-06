// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

// TODO(134732760): This is still work in progress.
// The SpecialLibraryConfiguration is a set of flags for experimentation
// with the desugared JDK libraries which should be replaced by a D8/R8
// API level flag --coreLibraryDescriptor
public class SpecialLibraryConfiguration {

  private static Map<String, String> buildPrefixRewritingForProgramCompilation() {
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

  private static Map<String, String> buildPrefixRewritingForProgramCompilationAndroidNPlus() {
    // From Android O, emulated interfaces are not supported and are not required.
    // Prefix rewriting is different to avoid rewriting classes to j$ while they should not,
    // else Android cannot find the library methods because they use the incorrect types.
    return ImmutableMap.<String, String>builder()
        .put("java.time.", "j$.time.")
        .put("java.util.Desugar", "j$.util.Desugar")
        .build();
  }

  private static Map<String, String> buildPrefixRewritingForCoreLibCompilation() {
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

  private static Map<String, String>
      buildRetargetCoreLibraryMemberForProgramCompilationAndroidNPlus() {
    // --retarget_core_library_member.
    return ImmutableMap.<String, String>builder()
        .put("java.util.Calendar#toInstant", "java.util.DesugarCalendar")
        .put("java.util.Date#from", "java.util.DesugarDate")
        .put("java.util.Date#toInstant", "java.util.DesugarDate")
        .put("java.util.GregorianCalendar#from", "java.util.DesugarGregorianCalendar")
        .put("java.util.GregorianCalendar#toZonedDateTime", "java.util.DesugarGregorianCalendar")
        .build();
  }

  private static Map<String, String> buildRetargetCoreLibraryMemberForProgramCompilation() {
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

  private static Map<String, String> buildRetargetCoreLibraryMemberForCoreLibCompilation() {
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

  private static List<String> buildDontRewriteInvocations() {
    // --dont_rewrite_core_library_invocation "java/util/Iterator#remove".
    return ImmutableList.of("java.util.Iterator#remove");
  }

  private static Map<String, String> buildEmulateLibraryInterface() {
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
        .put("java.util.List", "j$.util.List")
        .put("java.util.SortedSet", "j$.util.SortedSet")
        .put("java.util.Set", "j$.util.Set")
        .put("java.util.concurrent.ConcurrentMap", "j$.util.concurrent.ConcurrentMap")
        .build();
  }

  private static Map<String, String> buildBackportCoreLibraryMembers() {
    // R8 specific to deal with *8 removal.
    return ImmutableMap.<String, String>builder()
        .put("java.lang.Double8", "java.lang.Double")
        .put("java.lang.Integer8", "java.lang.Integer")
        .put("java.lang.Long8", "java.lang.Long")
        .build();
  }

  public static void configureLibraryDesugaringForProgramCompilation(InternalOptions options) {
    // TODO(b/134732760): Make assertions in D8/R8 commands.
    if (options.minApiLevel >= AndroidApiLevel.P.getLevel()) {
      options.reporter.warning(
          new StringDiagnostic(
              "Desugaring core libraries for Android P and over is possible but not required."));
    }
    options.coreLibraryCompilation = false;
    if (options.minApiLevel < AndroidApiLevel.N.getLevel()) {
      options.rewritePrefix = buildPrefixRewritingForProgramCompilation();
      options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForProgramCompilation();
      options.emulateLibraryInterface = buildEmulateLibraryInterface();
      options.dontRewriteInvocations = buildDontRewriteInvocations();
    } else {
      options.rewritePrefix = buildPrefixRewritingForProgramCompilationAndroidNPlus();
      options.retargetCoreLibMember =
          buildRetargetCoreLibraryMemberForProgramCompilationAndroidNPlus();
    }
  }

  public static void configureLibraryDesugaringForLibraryCompilation(InternalOptions options) {
    // TODO(b/134732760): Make assertions in L8 commands.
    if (options.minApiLevel >= AndroidApiLevel.P.getLevel()) {
      options.reporter.warning(
          new StringDiagnostic(
              "Desugaring core libraries for Android P and over is possible but not required."));
    }
    options.coreLibraryCompilation = true;
    options.backportCoreLibraryMembers = buildBackportCoreLibraryMembers();
    options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForCoreLibCompilation();
    options.rewritePrefix = buildPrefixRewritingForCoreLibCompilation();
    // The following is ignored starting from Android O.
    options.dontRewriteInvocations = buildDontRewriteInvocations();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }
}
