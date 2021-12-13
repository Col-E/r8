// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LegacyRewritingFlags {

  private final Map<String, String> rewritePrefix;
  private final Map<DexType, DexType> emulateLibraryInterface;
  private final Map<DexString, Map<DexType, DexType>> retargetCoreLibMember;
  private final Map<DexType, DexType> backportCoreLibraryMember;
  private final Map<DexType, DexType> customConversions;
  private final List<Pair<DexType, DexString>> dontRewriteInvocation;
  private final Set<DexType> dontRetargetLibMember;
  private final Set<DexType> wrapperConversions;

  LegacyRewritingFlags(
      Map<String, String> rewritePrefix,
      Map<DexType, DexType> emulateLibraryInterface,
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember,
      Map<DexType, DexType> backportCoreLibraryMember,
      Map<DexType, DexType> customConversions,
      List<Pair<DexType, DexString>> dontRewriteInvocation,
      Set<DexType> dontRetargetLibMember,
      Set<DexType> wrapperConversions) {
    this.rewritePrefix = rewritePrefix;
    this.emulateLibraryInterface = emulateLibraryInterface;
    this.retargetCoreLibMember = retargetCoreLibMember;
    this.backportCoreLibraryMember = backportCoreLibraryMember;
    this.customConversions = customConversions;
    this.dontRewriteInvocation = dontRewriteInvocation;
    this.dontRetargetLibMember = dontRetargetLibMember;
    this.wrapperConversions = wrapperConversions;
  }

  public static LegacyRewritingFlags empty() {
    return new LegacyRewritingFlags(
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableList.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  public static LegacyRewritingFlags withOnlyRewritePrefixForTesting(
      Map<String, String> prefix, InternalOptions options) {
    Builder builder = builder(options.dexItemFactory(), options.reporter, Origin.unknown());
    prefix.forEach(builder::putRewritePrefix);
    return builder.build();
  }

  public static Builder builder(DexItemFactory dexItemFactory, Reporter reporter, Origin origin) {
    return new Builder(dexItemFactory, reporter, origin);
  }

  public Builder newBuilder(DexItemFactory dexItemFactory, Reporter reporter, Origin origin) {
    return new Builder(
        dexItemFactory,
        reporter,
        origin,
        rewritePrefix,
        emulateLibraryInterface,
        retargetCoreLibMember,
        backportCoreLibraryMember,
        customConversions,
        dontRewriteInvocation,
        dontRetargetLibMember,
        wrapperConversions);
  }

  public Map<String, String> getRewritePrefix() {
    return rewritePrefix;
  }

  public Map<DexType, DexType> getEmulateLibraryInterface() {
    return emulateLibraryInterface;
  }

  public Map<DexString, Map<DexType, DexType>> getRetargetCoreLibMember() {
    return retargetCoreLibMember;
  }

  public Map<DexType, DexType> getBackportCoreLibraryMember() {
    return backportCoreLibraryMember;
  }

  public Map<DexType, DexType> getCustomConversions() {
    return customConversions;
  }

  public List<Pair<DexType, DexString>> getDontRewriteInvocation() {
    return dontRewriteInvocation;
  }

  public Set<DexType> getDontRetargetLibMember() {
    return dontRetargetLibMember;
  }

  public Set<DexType> getWrapperConversions() {
    return wrapperConversions;
  }

  public static class Builder {

    private final DexItemFactory factory;
    private final Reporter reporter;
    private final Origin origin;

    private final Map<String, String> rewritePrefix;
    private final Map<DexType, DexType> emulateLibraryInterface;
    private final Map<DexString, Map<DexType, DexType>> retargetCoreLibMember;
    private final Map<DexType, DexType> backportCoreLibraryMember;
    private final Map<DexType, DexType> customConversions;
    private final List<Pair<DexType, DexString>> dontRewriteInvocation;
    private final Set<DexType> dontRetargetLibMember;
    private final Set<DexType> wrapperConversions;

    Builder(DexItemFactory factory, Reporter reporter, Origin origin) {
      this(
          factory,
          reporter,
          origin,
          new HashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new ArrayList<>(),
          Sets.newIdentityHashSet(),
          Sets.newIdentityHashSet());
    }

    Builder(
        DexItemFactory factory,
        Reporter reporter,
        Origin origin,
        Map<String, String> rewritePrefix,
        Map<DexType, DexType> emulateLibraryInterface,
        Map<DexString, Map<DexType, DexType>> retargetCoreLibMember,
        Map<DexType, DexType> backportCoreLibraryMember,
        Map<DexType, DexType> customConversions,
        List<Pair<DexType, DexString>> dontRewriteInvocation,
        Set<DexType> dontRetargetLibMember,
        Set<DexType> wrapperConversions) {
      this.factory = factory;
      this.reporter = reporter;
      this.origin = origin;
      this.rewritePrefix = rewritePrefix;
      this.emulateLibraryInterface = emulateLibraryInterface;
      this.retargetCoreLibMember = retargetCoreLibMember;
      this.backportCoreLibraryMember = backportCoreLibraryMember;
      this.customConversions = customConversions;
      this.dontRewriteInvocation = dontRewriteInvocation;
      this.dontRetargetLibMember = dontRetargetLibMember;
      this.wrapperConversions = wrapperConversions;
    }

    // Utility to set values. Currently assumes the key is fresh.
    private <K, V> void put(Map<K, V> map, K key, V value, String desc) {
      if (map.containsKey(key)) {
        throw reporter.fatalError(
            new StringDiagnostic(
                "Invalid desugared library configuration. "
                    + " Duplicate assignment of key: '"
                    + key
                    + "' in sections for '"
                    + desc
                    + "'",
                origin));
      }
      map.put(key, value);
    }

    public Builder putRewritePrefix(String prefix, String rewrittenPrefix) {
      put(
          rewritePrefix,
          prefix,
          rewrittenPrefix,
          LegacyDesugaredLibrarySpecificationParser.REWRITE_PREFIX_KEY);
      return this;
    }

    public Builder putEmulateLibraryInterface(
        String emulateLibraryItf, String rewrittenEmulateLibraryItf) {
      DexType interfaceType = stringClassToDexType(emulateLibraryItf);
      DexType rewrittenType = stringClassToDexType(rewrittenEmulateLibraryItf);
      put(
          emulateLibraryInterface,
          interfaceType,
          rewrittenType,
          LegacyDesugaredLibrarySpecificationParser.EMULATE_INTERFACE_KEY);
      return this;
    }

    public Builder putCustomConversion(String type, String conversionHolder) {
      DexType dexType = stringClassToDexType(type);
      DexType conversionType = stringClassToDexType(conversionHolder);
      put(
          customConversions,
          dexType,
          conversionType,
          LegacyDesugaredLibrarySpecificationParser.CUSTOM_CONVERSION_KEY);
      return this;
    }

    public Builder addWrapperConversion(String type) {
      DexType dexType = stringClassToDexType(type);
      wrapperConversions.add(dexType);
      return this;
    }

    public Builder putRetargetCoreLibMember(String retarget, String rewrittenRetarget) {
      int index = sharpIndex(retarget, "retarget core library member");
      DexString methodName = factory.createString(retarget.substring(index + 1));
      retargetCoreLibMember.putIfAbsent(methodName, new IdentityHashMap<>());
      Map<DexType, DexType> typeMap = retargetCoreLibMember.get(methodName);
      DexType originalType = stringClassToDexType(retarget.substring(0, index));
      DexType finalType = stringClassToDexType(rewrittenRetarget);
      assert !typeMap.containsKey(originalType);
      put(
          typeMap,
          originalType,
          finalType,
          LegacyDesugaredLibrarySpecificationParser.RETARGET_LIB_MEMBER_KEY);
      return this;
    }

    public Builder putBackportCoreLibraryMember(String backport, String rewrittenBackport) {
      DexType backportType = stringClassToDexType(backport);
      DexType rewrittenBackportType = stringClassToDexType(rewrittenBackport);
      put(
          backportCoreLibraryMember,
          backportType,
          rewrittenBackportType,
          LegacyDesugaredLibrarySpecificationParser.BACKPORT_KEY);
      return this;
    }

    public Builder addDontRewriteInvocation(String dontRewriteInvocation) {
      int index = sharpIndex(dontRewriteInvocation, "don't rewrite");
      this.dontRewriteInvocation.add(
          new Pair<>(
              stringClassToDexType(dontRewriteInvocation.substring(0, index)),
              factory.createString(dontRewriteInvocation.substring(index + 1))));
      return this;
    }

    public Builder addDontRetargetLibMember(String dontRetargetLibMember) {
      this.dontRetargetLibMember.add(stringClassToDexType(dontRetargetLibMember));
      return this;
    }

    private int sharpIndex(String typeAndSelector, String descr) {
      int index = typeAndSelector.lastIndexOf('#');
      if (index <= 0 || index >= typeAndSelector.length() - 1) {
        throw new CompilationError(
            "Invalid " + descr + " specification (# position) in " + typeAndSelector + ".");
      }
      return index;
    }

    private DexType stringClassToDexType(String stringClass) {
      return factory.createType(DescriptorUtils.javaTypeToDescriptor(stringClass));
    }

    public LegacyRewritingFlags build() {
      validate();
      return new LegacyRewritingFlags(
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableMap.copyOf(emulateLibraryInterface),
          ImmutableMap.copyOf(retargetCoreLibMember),
          ImmutableMap.copyOf(backportCoreLibraryMember),
          ImmutableMap.copyOf(customConversions),
          ImmutableList.copyOf(dontRewriteInvocation),
          ImmutableSet.copyOf(dontRetargetLibMember),
          ImmutableSet.copyOf(wrapperConversions));
    }

    private void validate() {
      SetView<DexType> dups = Sets.intersection(customConversions.keySet(), wrapperConversions);
      if (!dups.isEmpty()) {
        throw reporter.fatalError(
            new StringDiagnostic(
                "Invalid desugared library configuration. "
                    + "Duplicate types in custom conversions and wrapper conversions: "
                    + String.join(
                        ", ", dups.stream().map(DexType::toString).collect(Collectors.toSet())),
                origin));
      }
    }
  }
}
