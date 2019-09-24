// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

// TODO(134732760): This is still work in progress.
// The DesugaredLibraryConfigurationForTesting is a set of flags for experimentation
// with the desugared JDK libraries which should be replaced by a D8/R8
// API level flag --coreLibraryDescriptor
public class DesugaredLibraryConfigurationForTesting {

  private static Map<String, String> buildPrefixRewritingForProgramCompilationAllAndroid() {
    return ImmutableMap.<String, String>builder()
        .put("java.util.concurrent.ConcurrentHashMap", "j$.util.concurrent.ConcurrentHashMap")
        .put("java.util.stream.", "j$.util.stream.")
        .put("java.util.function.", "j$.util.function.")
        .put("java.util.DoubleSummaryStatistics", "j$.util.DoubleSummaryStatistics")
        .put("java.util.IntSummaryStatistics", "j$.util.IntSummaryStatistics")
        .put("java.util.LongSummaryStatistics", "j$.util.LongSummaryStatistics")
        .put("java.util.Optional", "j$.util.Optional")
        .put("java.util.PrimitiveIterator", "j$.util.PrimitiveIterator")
        .put("java.util.Spliterator", "j$.util.Spliterator")
        .put("java.util.StringJoiner", "j$.util.StringJoiner")
        .put("java.util.concurrent.ThreadLocalRandom", "j$.util.concurrent.ThreadLocalRandom")
        .put("java.util.concurrent.atomic.DesugarAtomic", "j$.util.concurrent.atomic.DesugarAtomic")
        .putAll(buildPrefixRewritingForProgramCompilationAndroidNPlus())
        .build();
  }

  private static Map<String, String> buildPrefixRewritingForProgramCompilationAndroidNPlus() {
    return ImmutableMap.<String, String>builder()
        .put("java.time.", "j$.time.")
        .put("java.util.Desugar", "j$.util.Desugar")
        .build();
  }

  private static Map<String, String>
      buildRetargetCoreLibraryMemberForProgramCompilationAllAndroid() {
    return ImmutableMap.<String, String>builder()
        .put("java.util.Arrays#stream", "java.util.DesugarArrays")
        .put("java.util.Arrays#spliterator", "java.util.DesugarArrays")
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
        .putAll(buildRetargetCoreLibraryMemberForProgramCompilationAndroidNPlus())
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

  private static Map<String, String> buildPrefixRewritingForCoreLibCompilationAllAndroid() {
    return ImmutableMap.<String, String>builder()
        .put("java.util.concurrent.ConcurrentHashMap", "j$.util.concurrent.ConcurrentHashMap")
        .put("java.util.stream.", "j$.util.stream.")
        .put("java.util.function.", "j$.util.function.")
        .put("java.util.Comparators", "j$.util.Comparators")
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
        .put("java.util.concurrent.DesugarUnsafe", "j$.util.concurrent.DesugarUnsafe")
        .put("java.util.concurrent.ThreadLocalRandom", "j$.util.concurrent.ThreadLocalRandom")
        .put("java.util.concurrent.atomic.DesugarAtomic", "j$.util.concurrent.atomic.DesugarAtomic")
        .putAll(buildPrefixRewritingForCoreLibCompilationAndroidNPlus())
        .build();
  }

  private static Map<String, String> buildPrefixRewritingForCoreLibCompilationAndroidNPlus() {
    return ImmutableMap.<String, String>builder()
        .put("j$.time.", "java.time.")
        .put("java.time.", "j$.time.")
        .put("java.util.Desugar", "j$.util.Desugar")
        .build();
  }

  private static Map<String, String> buildRetargetCoreLibraryMemberForCoreLibCompilation() {
    return ImmutableMap.<String, String>builder()
        .put("java.util.LinkedHashSet#spliterator", "java.util.DesugarLinkedHashSet")
        // Following 2 for testing only (core library with extensions).
        .put("java.util.Arrays#stream", "java.util.DesugarArrays")
        .put("java.util.Arrays#spliterator", "java.util.DesugarArrays")
        .build();
  }

  private static List<String> buildDontRewriteInvocations() {
    return ImmutableList.of("java.util.Iterator#remove");
  }

  private static Map<String, String> buildEmulateLibraryInterface() {
    return ImmutableMap.<String, String>builder()
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
        .put("java.lang.Math8", "java.lang.Math")
        .build();
  }

  public static DesugaredLibraryConfiguration configureLibraryDesugaringForProgramCompilation(
      int minApiLevel, DexItemFactory factory) {
    if (minApiLevel >= AndroidApiLevel.O.getLevel()) {
      return DesugaredLibraryConfiguration.empty();
    }
    Map<String, String> rewritePrefix;
    Map<String, String> retargetCoreLibMember;
    Map<String, String> emulateLibraryInterface = ImmutableMap.of();
    List<String> dontRewriteInvocations = ImmutableList.of();
    if (minApiLevel < AndroidApiLevel.N.getLevel()) {
      rewritePrefix = buildPrefixRewritingForProgramCompilationAllAndroid();
      retargetCoreLibMember = buildRetargetCoreLibraryMemberForProgramCompilationAllAndroid();
      emulateLibraryInterface = buildEmulateLibraryInterface();
      dontRewriteInvocations = buildDontRewriteInvocations();
    } else {
      rewritePrefix = buildPrefixRewritingForProgramCompilationAndroidNPlus();
      retargetCoreLibMember = buildRetargetCoreLibraryMemberForProgramCompilationAndroidNPlus();
    }
    return createDesugaredLibraryConfiguration(
        factory,
        false,
        rewritePrefix,
        emulateLibraryInterface,
        retargetCoreLibMember,
        ImmutableMap.of(),
        dontRewriteInvocations);
  }

  public static DesugaredLibraryConfiguration configureLibraryDesugaringForLibraryCompilation(
      int minApiLevel, DexItemFactory factory) {
    if (minApiLevel >= AndroidApiLevel.O.getLevel()) {
      return DesugaredLibraryConfiguration.empty();
    }
    Map<String, String> rewritePrefix;
    Map<String, String> retargetCoreLibMember = ImmutableMap.of();
    Map<String, String> emulateLibraryInterface = ImmutableMap.of();
    List<String> dontRewriteInvocations = ImmutableList.of();
    Map<String, String> backportCoreLibraryMembers = buildBackportCoreLibraryMembers();
    if (minApiLevel < AndroidApiLevel.N.getLevel()) {
      retargetCoreLibMember = buildRetargetCoreLibraryMemberForCoreLibCompilation();
      dontRewriteInvocations = buildDontRewriteInvocations();
      emulateLibraryInterface = buildEmulateLibraryInterface();
      rewritePrefix = buildPrefixRewritingForCoreLibCompilationAllAndroid();
    } else {
      rewritePrefix = buildPrefixRewritingForCoreLibCompilationAndroidNPlus();
    }
    return createDesugaredLibraryConfiguration(
        factory,
        true,
        rewritePrefix,
        emulateLibraryInterface,
        retargetCoreLibMember,
        backportCoreLibraryMembers,
        dontRewriteInvocations);
  }

  public static DesugaredLibraryConfiguration createDesugaredLibraryConfiguration(
      DexItemFactory factory,
      boolean libraryCompilation,
      Map<String, String> rewritePrefix,
      Map<String, String> emulateLibraryInterface,
      Map<String, String> retargetCoreLibMember,
      Map<String, String> backportCoreLibraryMembers,
      List<String> dontRewriteInvocations) {
    DesugaredLibraryConfiguration.Builder builder = DesugaredLibraryConfiguration.builder(factory);
    if (libraryCompilation) {
      builder.setLibraryCompilation();
    } else {
      builder.setProgramCompilation();
    }
    for (String key : rewritePrefix.keySet()) {
      builder.putRewritePrefix(key, rewritePrefix.get(key));
    }
    for (String key : emulateLibraryInterface.keySet()) {
      builder.putEmulateLibraryInterface(key, emulateLibraryInterface.get(key));
    }
    for (String key : retargetCoreLibMember.keySet()) {
      builder.putRetargetCoreLibMember(key, retargetCoreLibMember.get(key));
    }
    for (String key : backportCoreLibraryMembers.keySet()) {
      builder.putBackportCoreLibraryMember(key, backportCoreLibraryMembers.get(key));
    }
    for (String key : dontRewriteInvocations) {
      builder.addDontRewriteInvocation(key);
    }
    return builder.build();
  }
}
