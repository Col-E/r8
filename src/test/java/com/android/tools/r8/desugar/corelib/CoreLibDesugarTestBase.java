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
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        .put("java.lang.Double8", "j$.lang.Double8")
        .put("java.lang.Integer8", "j$.lang.Integer8")
        .put("java.lang.Long8", "j$.lang.Long8")
        .put("java.lang.Math8", "j$.lang.Math8")
        .put("java.io.Desugar", "j$.io.Desugar")
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

  private Map<String, String> buildEmulateLibraryInterface() {
    return ImmutableMap.<String, String>builder()
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
