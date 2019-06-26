// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Map;

public class CoreLibDesugarTestBase extends TestBase {
  private Map<String, String> buildPrefixRewriting() {
    // TODO(134732760): Make 2 different methods when compiling core_library or not.
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        .put("java.lang.Double8", "j$.lang.Double8")
        .put("java.lang.Integer8", "j$.lang.Integer8")
        .put("java.lang.Long8", "j$.lang.Long8")
        .put("java.lang.Math8", "j$.lang.Math8")
        .put("java.io.Desugar", "j$.io.Desugar")
        // TODO(134732760): I do not see UncheckedIOException in Bazel
        .put("java.io.UncheckedIOException", "j$.io.UncheckedIOException")
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

  // TODO(134732760): Following flag when compiling a core_library.
  // --core_library.

  // TODO(134732760): Following flag when compiling a core_library.
  // --retarget_core_library_member
  // "java/util/LinkedHashSet#spliterator->java/util/DesugarLinkedHashSet".

  // TODO(134732760): Following flags when compiling the program using the core_library
  // --retarget_core_library_member "java/lang/Double#max->java/lang/Double8" \
  // --retarget_core_library_member "java/lang/Double#min->java/lang/Double8" \
  // --retarget_core_library_member "java/lang/Double#sum->java/lang/Double8" \
  // --retarget_core_library_member "java/lang/Integer#max->java/lang/Integer8" \
  // --retarget_core_library_member "java/lang/Integer#min->java/lang/Integer8" \
  // --retarget_core_library_member "java/lang/Integer#sum->java/lang/Integer8" \
  // --retarget_core_library_member "java/lang/Long#max->java/lang/Long8" \
  // --retarget_core_library_member "java/lang/Long#min->java/lang/Long8" \
  // --retarget_core_library_member "java/lang/Long#sum->java/lang/Long8" \
  // --retarget_core_library_member "java/lang/Math#toIntExact->java/lang/Math8" \
  // --retarget_core_library_member "java/util/Arrays#stream->java/util/DesugarArrays" \
  // --retarget_core_library_member "java/util/Arrays#spliterator->java/util/DesugarArrays" \
  // --retarget_core_library_member "java/util/Calendar#toInstant->java/util/DesugarCalendar" \
  // --retarget_core_library_member "java/util/Date#from->java/util/DesugarDate" \
  // --retarget_core_library_member "java/util/Date#toInstant->java/util/DesugarDate" \
  // --retarget_core_library_member
  // "java/util/GregorianCalendar#from->java/util/DesugarGregorianCalendar" \
  // --retarget_core_library_member
  // "java/util/GregorianCalendar#toZonedDateTime->java/util/DesugarGregorianCalendar" \
  // --retarget_core_library_member
  // "java/util/LinkedHashSet#spliterator->java/util/DesugarLinkedHashSet" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicInteger#getAndUpdate->java/util/concurrent/atomic/DesugarAtomicInteger" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicInteger#updateAndGet->java/util/concurrent/atomic/DesugarAtomicInteger" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicInteger#getAndAccumulate->java/util/concurrent/atomic/DesugarAtomicInteger" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicInteger#accumulateAndGet->java/util/concurrent/atomic/DesugarAtomicInteger" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicLong#getAndUpdate->java/util/concurrent/atomic/DesugarAtomicLong" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicLong#updateAndGet->java/util/concurrent/atomic/DesugarAtomicLong" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicLong#getAndAccumulate->java/util/concurrent/atomic/DesugarAtomicLong" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicLong#accumulateAndGet->java/util/concurrent/atomic/DesugarAtomicLong" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicReference#getAndUpdate->java/util/concurrent/atomic/DesugarAtomicReference" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicReference#updateAndGet->java/util/concurrent/atomic/DesugarAtomicReference" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicReference#getAndAccumulate->java/util/concurrent/atomic/DesugarAtomicReference" \
  // --retarget_core_library_member
  // "java/util/concurrent/atomic/AtomicReference#accumulateAndGet->java/util/concurrent/atomic/DesugarAtomicReference" .

  // TODO(134732760): Following flag for both compilation.
  // --dont_rewrite_core_library_invocation "java/util/Iterator#remove"

  private Map<String, String> buildEmulateLibraryInterface() {
    return ImmutableMap.<String, String>builder()
        // Following flags for both compilation.
        // --emulate_core_library_interface.
        .put("java.util.Collection", "j$.util.Collection")
        .put("java.util.Map", "j$.util.Map")
        .put("java.util.Map$Entry", "j$.util.Map$Entry")
        .put("java.util.Iterator", "j$.util.Iterator")
        .put("java.util.Comparator", "j$.util.Comparator")
        .build();
  }

  protected void configureCoreLibDesugar(InternalOptions options) {
    options.rewritePrefix = buildPrefixRewriting();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) throws Exception {
    return testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(ToolHelper.getDesugarJDKLibs())
        .addOptionsModification(this::configureCoreLibDesugar)
        .setMinApi(apiLevel)
        .compile()
        .writeToZip();
  }

  protected Path buildDesugaredLibrary(TestRuntime runtime) throws Exception {
    return buildDesugaredLibrary(runtime.asDex().getMinApiLevel());
  }
}
