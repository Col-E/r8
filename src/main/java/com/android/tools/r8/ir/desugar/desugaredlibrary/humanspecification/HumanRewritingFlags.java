// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HumanRewritingFlags {

  private final Map<String, String> rewritePrefix;
  private final Map<DexType, DexType> emulateLibraryInterface;
  private final Map<DexMethod, DexType> retargetCoreLibMember;
  private final Map<DexType, DexType> backportCoreLibraryMember;
  private final Map<DexType, DexType> customConversions;
  private final Set<DexMethod> dontRewriteInvocation;
  private final Set<DexType> dontRetargetLibMember;
  private final Set<DexType> wrapperConversions;

  HumanRewritingFlags(
      Map<String, String> rewritePrefix,
      Map<DexType, DexType> emulateLibraryInterface,
      Map<DexMethod, DexType> retargetCoreLibMember,
      Map<DexType, DexType> backportCoreLibraryMember,
      Map<DexType, DexType> customConversions,
      Set<DexMethod> dontRewriteInvocation,
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

  public static HumanRewritingFlags empty() {
    return new HumanRewritingFlags(
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  public static HumanRewritingFlags withOnlyRewritePrefixForTesting(
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

  public Map<DexMethod, DexType> getRetargetCoreLibMember() {
    return retargetCoreLibMember;
  }

  public Map<DexType, DexType> getBackportCoreLibraryMember() {
    return backportCoreLibraryMember;
  }

  public Map<DexType, DexType> getCustomConversions() {
    return customConversions;
  }

  public Set<DexMethod> getDontRewriteInvocation() {
    return dontRewriteInvocation;
  }

  public Set<DexType> getDontRetargetLibMember() {
    return dontRetargetLibMember;
  }

  public Set<DexType> getWrapperConversions() {
    return wrapperConversions;
  }

  public static class Builder {

    private static final String SEPARATORS = "\\s+|,\\s+|#|\\(|\\)";

    private final DexItemFactory factory;
    private final Reporter reporter;
    private final Origin origin;

    private final Map<String, String> rewritePrefix;
    private final Map<DexType, DexType> emulateLibraryInterface;
    private final Map<DexMethod, DexType> retargetCoreLibMember;
    private final Map<DexType, DexType> backportCoreLibraryMember;
    private final Map<DexType, DexType> customConversions;
    private final Set<DexMethod> dontRewriteInvocation;
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
          Sets.newIdentityHashSet(),
          Sets.newIdentityHashSet(),
          Sets.newIdentityHashSet());
    }

    Builder(
        DexItemFactory factory,
        Reporter reporter,
        Origin origin,
        Map<String, String> rewritePrefix,
        Map<DexType, DexType> emulateLibraryInterface,
        Map<DexMethod, DexType> retargetCoreLibMember,
        Map<DexType, DexType> backportCoreLibraryMember,
        Map<DexType, DexType> customConversions,
        Set<DexMethod> dontRewriteInvocation,
        Set<DexType> dontRetargetLibMember,
        Set<DexType> wrapperConversions) {
      this.factory = factory;
      this.reporter = reporter;
      this.origin = origin;
      this.rewritePrefix = new HashMap<>(rewritePrefix);
      this.emulateLibraryInterface = new IdentityHashMap<>(emulateLibraryInterface);
      this.retargetCoreLibMember = new IdentityHashMap<>(retargetCoreLibMember);
      this.backportCoreLibraryMember = new IdentityHashMap<>(backportCoreLibraryMember);
      this.customConversions = new IdentityHashMap<>(customConversions);
      this.dontRewriteInvocation = Sets.newIdentityHashSet();
      this.dontRewriteInvocation.addAll(dontRewriteInvocation);
      this.dontRetargetLibMember = Sets.newIdentityHashSet();
      this.dontRetargetLibMember.addAll(dontRetargetLibMember);
      this.wrapperConversions = Sets.newIdentityHashSet();
      this.wrapperConversions.addAll(wrapperConversions);
    }

    // Utility to set values.
    private <K, V> void put(Map<K, V> map, K key, V value, String desc) {
      if (map.containsKey(key) && !map.get(key).equals(value)) {
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
          HumanDesugaredLibrarySpecificationParser.REWRITE_PREFIX_KEY);
      return this;
    }

    public Builder putEmulateLibraryInterface(
        String emulateLibraryItf, String rewrittenEmulateLibraryItf) {
      DexType interfaceType = stringClassToDexType(emulateLibraryItf);
      DexType rewrittenType = stringClassToDexType(rewrittenEmulateLibraryItf);
      putEmulateLibraryInterface(interfaceType, rewrittenType);
      return this;
    }

    public Builder putEmulateLibraryInterface(DexType interfaceType, DexType rewrittenType) {
      put(
          emulateLibraryInterface,
          interfaceType,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.EMULATE_INTERFACE_KEY);
      return this;
    }

    public Builder putCustomConversion(String type, String conversionHolder) {
      DexType dexType = stringClassToDexType(type);
      DexType conversionType = stringClassToDexType(conversionHolder);
      putCustomConversion(dexType, conversionType);
      return this;
    }

    public Builder putCustomConversion(DexType dexType, DexType conversionType) {
      put(
          customConversions,
          dexType,
          conversionType,
          HumanDesugaredLibrarySpecificationParser.CUSTOM_CONVERSION_KEY);
      return this;
    }

    public Builder addWrapperConversion(String type) {
      DexType dexType = stringClassToDexType(type);
      addWrapperConversion(dexType);
      return this;
    }

    public Builder addWrapperConversion(DexType dexType) {
      wrapperConversions.add(dexType);
      return this;
    }

    public Builder putRetargetCoreLibMember(String retarget, String rewrittenRetarget) {
      DexMethod key = parseMethod(retarget);
      DexType rewrittenType = stringClassToDexType(rewrittenRetarget);
      putRetargetCoreLibMember(key, rewrittenType);
      return this;
    }

    public Builder putRetargetCoreLibMember(DexMethod key, DexType rewrittenType) {
      put(
          retargetCoreLibMember,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.RETARGET_LIB_MEMBER_KEY);
      return this;
    }

    public Builder putBackportCoreLibraryMember(String backport, String rewrittenBackport) {
      DexType backportType = stringClassToDexType(backport);
      DexType rewrittenBackportType = stringClassToDexType(rewrittenBackport);
      putBackportCoreLibraryMember(backportType, rewrittenBackportType);
      return this;
    }

    public Builder putBackportCoreLibraryMember(
        DexType backportType, DexType rewrittenBackportType) {
      put(
          backportCoreLibraryMember,
          backportType,
          rewrittenBackportType,
          HumanDesugaredLibrarySpecificationParser.BACKPORT_KEY);
      return this;
    }

    public Builder addDontRewriteInvocation(String dontRewriteInvocation) {
      DexMethod dontRewrite = parseMethod(dontRewriteInvocation);
      addDontRewriteInvocation(dontRewrite);
      return this;
    }

    public Builder addDontRewriteInvocation(DexMethod dontRewrite) {
      this.dontRewriteInvocation.add(dontRewrite);
      return this;
    }

    public Builder addDontRetargetLibMember(String dontRetargetLibMember) {
      addDontRetargetLibMember(stringClassToDexType(dontRetargetLibMember));
      return this;
    }

    public Builder addDontRetargetLibMember(DexType dontRetargetLibMember) {
      this.dontRetargetLibMember.add(dontRetargetLibMember);
      return this;
    }

    private DexMethod parseMethod(String signature) {
      String[] split = signature.split(SEPARATORS);
      assert split.length >= 3;
      DexType returnType = factory.createType(DescriptorUtils.javaTypeToDescriptor(split[0]));
      DexType holderType = factory.createType(DescriptorUtils.javaTypeToDescriptor(split[1]));
      DexString name = factory.createString(split[2]);
      DexType[] argTypes = new DexType[split.length - 3];
      for (int i = 3; i < split.length; i++) {
        argTypes[i - 3] = factory.createType(DescriptorUtils.javaTypeToDescriptor(split[i]));
      }
      DexProto proto = factory.createProto(returnType, argTypes);
      return factory.createMethod(holderType, proto, name);
    }

    private DexType stringClassToDexType(String stringClass) {
      return factory.createType(DescriptorUtils.javaTypeToDescriptor(stringClass));
    }

    public HumanRewritingFlags build() {
      validate();
      return new HumanRewritingFlags(
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableMap.copyOf(emulateLibraryInterface),
          ImmutableMap.copyOf(retargetCoreLibMember),
          ImmutableMap.copyOf(backportCoreLibraryMember),
          ImmutableMap.copyOf(customConversions),
          ImmutableSet.copyOf(dontRewriteInvocation),
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
