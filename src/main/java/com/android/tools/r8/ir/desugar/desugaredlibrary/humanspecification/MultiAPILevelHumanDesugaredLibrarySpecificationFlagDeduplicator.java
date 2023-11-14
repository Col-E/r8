// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags.HumanEmulatedInterfaceDescriptor;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MultiAPILevelHumanDesugaredLibrarySpecificationFlagDeduplicator {

  public static void deduplicateFlags(
      MultiAPILevelHumanDesugaredLibrarySpecification specification,
      Reporter reporter) {

    Set<ApiLevelRange> apis = new HashSet<>();
    apis.addAll(specification.getCommonFlags().keySet());
    apis.addAll(specification.getLibraryFlags().keySet());
    apis.addAll(specification.getProgramFlags().keySet());

    for (ApiLevelRange api : apis) {
      deduplicateFlags(specification, reporter, api);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private static void deduplicateFlags(
      MultiAPILevelHumanDesugaredLibrarySpecification specification,
      Reporter reporter,
      ApiLevelRange api) {

    Map<ApiLevelRange, HumanRewritingFlags> commonFlags = specification.getCommonFlags();
    Map<ApiLevelRange, HumanRewritingFlags> libraryFlags = specification.getLibraryFlags();
    Map<ApiLevelRange, HumanRewritingFlags> programFlags = specification.getProgramFlags();

    HumanRewritingFlags library = libraryFlags.get(api);
    HumanRewritingFlags program = programFlags.get(api);

    if (library == null || program == null) {
      return;
    }

    Origin origin = specification.getOrigin();
    HumanRewritingFlags.Builder commonBuilder =
        commonFlags.get(api) == null
            ? HumanRewritingFlags.builder(reporter, origin)
            : commonFlags.get(api).newBuilder(reporter, origin);
    HumanRewritingFlags.Builder libraryBuilder = HumanRewritingFlags.builder(reporter, origin);
    HumanRewritingFlags.Builder programBuilder = HumanRewritingFlags.builder(reporter, origin);

    // Iterate over all library/program flags, add them in common if also in the other, else add
    // them to library/program.
    deduplicateFlags(library, program, commonBuilder, libraryBuilder);
    deduplicateFlags(program, library, commonBuilder, programBuilder);

    putNewFlags(api, commonFlags, commonBuilder);
    putNewFlags(api, libraryFlags, libraryBuilder);
    putNewFlags(api, programFlags, programBuilder);
  }

  private static void putNewFlags(
      ApiLevelRange api,
      Map<ApiLevelRange, HumanRewritingFlags> flags,
      HumanRewritingFlags.Builder builder) {
    HumanRewritingFlags build = builder.build();
    if (build.isEmpty()) {
      flags.remove(api);
    } else {
      flags.put(api, build);
    }
  }

  private static void deduplicateFlags(
      HumanRewritingFlags flags,
      HumanRewritingFlags otherFlags,
      HumanRewritingFlags.Builder commonBuilder,
      HumanRewritingFlags.Builder builder) {
    deduplicateRewritePrefix(flags, otherFlags, commonBuilder, builder);
    deduplicateRewriteDifferentPrefix(flags, otherFlags, commonBuilder, builder);

    deduplicateEmulatedInterfaceFlags(
        flags.getEmulatedInterfaces(),
        otherFlags.getEmulatedInterfaces(),
        commonBuilder::putSpecifiedEmulatedInterface,
        builder::putSpecifiedEmulatedInterface);
    deduplicateFlags(
        flags.getRetargetMethodToType(),
        otherFlags.getRetargetMethodToType(),
        commonBuilder::retargetMethodToType,
        builder::retargetMethodToType);
    deduplicateFlags(
        flags.getRetargetMethodEmulatedDispatchToType(),
        otherFlags.getRetargetMethodEmulatedDispatchToType(),
        commonBuilder::retargetMethodEmulatedDispatchToType,
        builder::retargetMethodEmulatedDispatchToType);
    deduplicateFlags(
        flags.getLegacyBackport(),
        otherFlags.getLegacyBackport(),
        commonBuilder::putLegacyBackport,
        builder::putLegacyBackport);
    deduplicateFlags(
        flags.getCustomConversions(),
        otherFlags.getCustomConversions(),
        commonBuilder::putCustomConversion,
        builder::putCustomConversion);

    deduplicateFlags(
        flags.getDontRetarget(),
        otherFlags.getDontRetarget(),
        commonBuilder::addDontRetargetLibMember,
        builder::addDontRetargetLibMember);

    deduplicateWrapperFlags(flags, otherFlags, commonBuilder, builder);

    deduplicateAmendLibraryMemberFlags(flags, otherFlags, commonBuilder, builder);
  }

  private static void deduplicateWrapperFlags(
      HumanRewritingFlags flags,
      HumanRewritingFlags otherFlags,
      HumanRewritingFlags.Builder commonBuilder,
      HumanRewritingFlags.Builder builder) {
    Map<DexType, Set<DexMethod>> other = otherFlags.getWrapperConversions();
    flags
        .getWrapperConversions()
        .forEach(
            (wrapperType, excludedMethods) -> {
              if (other.containsKey(wrapperType)) {
                assert excludedMethods.equals(other.get(wrapperType));
                commonBuilder.addWrapperConversion(wrapperType, excludedMethods);
              } else {
                builder.addWrapperConversion(wrapperType, excludedMethods);
              }
            });
  }

  @SuppressWarnings("ReferenceEquality")
  private static void deduplicateAmendLibraryMemberFlags(
      HumanRewritingFlags flags,
      HumanRewritingFlags otherFlags,
      HumanRewritingFlags.Builder commonBuilder,
      HumanRewritingFlags.Builder builder) {
    Map<DexMethod, MethodAccessFlags> other = otherFlags.getAmendLibraryMethod();
    flags
        .getAmendLibraryMethod()
        .forEach(
            (k, v) -> {
              if (other.get(k) == v) {
                commonBuilder.amendLibraryMethod(k, v);
              } else {
                builder.amendLibraryMethod(k, v);
              }
            });
  }

  private static void deduplicateRewriteDifferentPrefix(
      HumanRewritingFlags flags,
      HumanRewritingFlags otherFlags,
      HumanRewritingFlags.Builder commonBuilder,
      HumanRewritingFlags.Builder builder) {
    flags
        .getRewriteDerivedPrefix()
        .forEach(
            (prefixToMatch, rewriteRules) -> {
              if (!otherFlags.getRewriteDerivedPrefix().containsKey(prefixToMatch)) {
                rewriteRules.forEach(
                    (k, v) -> builder.putRewriteDerivedPrefix(prefixToMatch, k, v));
              } else {
                Map<String, String> otherMap =
                    otherFlags.getRewriteDerivedPrefix().get(prefixToMatch);
                rewriteRules.forEach(
                    (k, v) -> {
                      if (otherMap.containsKey(k) && otherMap.get(k).equals(v)) {
                        commonBuilder.putRewriteDerivedPrefix(prefixToMatch, k, v);
                      } else {
                        builder.putRewriteDerivedPrefix(prefixToMatch, k, v);
                      }
                    });
              }
            });
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
              if (otherFlags.getRewritePrefix().containsKey(k)
                  && otherFlags.getRewritePrefix().get(k).equals(v)) {
                commonBuilder.putRewritePrefix(k, v);
              } else {
                builder.putRewritePrefix(k, v);
              }
            });
  }

  @SuppressWarnings("ReferenceEquality")
  private static <T extends DexItem> void deduplicateEmulatedInterfaceFlags(
      Map<T, HumanEmulatedInterfaceDescriptor> flags,
      Map<T, HumanEmulatedInterfaceDescriptor> otherFlags,
      BiConsumer<T, HumanEmulatedInterfaceDescriptor> common,
      BiConsumer<T, HumanEmulatedInterfaceDescriptor> specific) {
    flags.forEach(
        (k, v) -> {
          if (otherFlags.get(k).equals(v)) {
            common.accept(k, v);
          } else {
            specific.accept(k, v);
          }
        });
  }

  @SuppressWarnings("ReferenceEquality")
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
