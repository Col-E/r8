// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MultiAPILevelHumanDesugaredLibrarySpecificationFlagDeduplicator {

  public static void deduplicateFlags(
      MultiAPILevelHumanDesugaredLibrarySpecification specification,
      DexItemFactory factory,
      Reporter reporter) {

    IntArraySet apis = new IntArraySet();
    apis.addAll(specification.getCommonFlags().keySet());
    apis.addAll(specification.getLibraryFlags().keySet());
    apis.addAll(specification.getProgramFlags().keySet());

    for (Integer api : apis) {
      deduplicateFlags(specification, factory, reporter, api);
    }
  }

  private static void deduplicateFlags(
      MultiAPILevelHumanDesugaredLibrarySpecification specification,
      DexItemFactory factory,
      Reporter reporter,
      int api) {

    Int2ObjectMap<HumanRewritingFlags> commonFlags = specification.getCommonFlags();
    Int2ObjectMap<HumanRewritingFlags> libraryFlags = specification.getLibraryFlags();
    Int2ObjectMap<HumanRewritingFlags> programFlags = specification.getProgramFlags();

    HumanRewritingFlags library = libraryFlags.get(api);
    HumanRewritingFlags program = programFlags.get(api);

    if (library == null || program == null) {
      return;
    }

    Origin origin = specification.getOrigin();
    HumanRewritingFlags.Builder commonBuilder =
        commonFlags.get(api) == null
            ? HumanRewritingFlags.builder(factory, reporter, origin)
            : commonFlags.get(api).newBuilder(factory, reporter, origin);
    HumanRewritingFlags.Builder libraryBuilder =
        HumanRewritingFlags.builder(factory, reporter, origin);
    HumanRewritingFlags.Builder programBuilder =
        HumanRewritingFlags.builder(factory, reporter, origin);

    // Iterate over all library/program flags, add them in common if also in the other, else add
    // them to library/program.
    deduplicateFlags(library, program, commonBuilder, libraryBuilder);
    deduplicateFlags(program, library, commonBuilder, programBuilder);

    commonFlags.put(api, commonBuilder.build());
    libraryFlags.put(api, libraryBuilder.build());
    programFlags.put(api, programBuilder.build());
  }

  private static void deduplicateFlags(
      HumanRewritingFlags flags,
      HumanRewritingFlags otherFlags,
      HumanRewritingFlags.Builder commonBuilder,
      HumanRewritingFlags.Builder builder) {
    deduplicateRewritePrefix(flags, otherFlags, commonBuilder, builder);

    deduplicateFlags(
        flags.getEmulateLibraryInterface(),
        otherFlags.getEmulateLibraryInterface(),
        commonBuilder::putEmulateLibraryInterface,
        builder::putEmulateLibraryInterface);
    deduplicateFlags(
        flags.getRetargetCoreLibMember(),
        otherFlags.getRetargetCoreLibMember(),
        commonBuilder::putRetargetCoreLibMember,
        builder::putRetargetCoreLibMember);
    deduplicateFlags(
        flags.getBackportCoreLibraryMember(),
        otherFlags.getBackportCoreLibraryMember(),
        commonBuilder::putBackportCoreLibraryMember,
        builder::putBackportCoreLibraryMember);
    deduplicateFlags(
        flags.getCustomConversions(),
        otherFlags.getCustomConversions(),
        commonBuilder::putCustomConversion,
        builder::putCustomConversion);

    deduplicateFlags(
        flags.getDontRewriteInvocation(),
        otherFlags.getDontRewriteInvocation(),
        commonBuilder::addDontRewriteInvocation,
        builder::addDontRewriteInvocation);
    deduplicateFlags(
        flags.getDontRetargetLibMember(),
        otherFlags.getDontRetargetLibMember(),
        commonBuilder::addDontRetargetLibMember,
        builder::addDontRetargetLibMember);
    deduplicateFlags(
        flags.getWrapperConversions(),
        otherFlags.getWrapperConversions(),
        commonBuilder::addWrapperConversion,
        builder::addWrapperConversion);
  }

  private static void deduplicateRewritePrefix(
      HumanRewritingFlags flags,
      HumanRewritingFlags otherFlags,
      HumanRewritingFlags.Builder commonBuilder,
      HumanRewritingFlags.Builder builder) {
    flags
        .getRewritePrefix()
        .forEach(
            (k, v) -> {
              if (otherFlags.getRewritePrefix().get(k) != null
                  && otherFlags.getRewritePrefix().get(k).equals(v)) {
                commonBuilder.putRewritePrefix(k, v);
              } else {
                builder.putRewritePrefix(k, v);
              }
            });
  }

  private static <T extends DexItem> void deduplicateFlags(
      Map<T, DexType> flags,
      Map<T, DexType> otherFlags,
      BiConsumer<T, DexType> common,
      BiConsumer<T, DexType> specific) {
    flags.forEach(
        (k, v) -> {
          if (otherFlags.get(k) == v) {
            common.accept(k, v);
          } else {
            specific.accept(k, v);
          }
        });
  }

  private static <T extends DexItem> void deduplicateFlags(
      Set<T> flags, Set<T> otherFlags, Consumer<T> common, Consumer<T> specific) {
    flags.forEach(
        e -> {
          if (otherFlags.contains(e)) {
            common.accept(e);
          } else {
            specific.accept(e);
          }
        });
  }
}
