// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.PrefixRewritingMapper.DesugarPrefixRewritingMapper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DesugaredLibraryConfiguration {

  // TODO(b/134732760): should use DexString, DexType, DexMethod or so on when possible.
  private final boolean libraryCompilation;
  private final Map<String, String> rewritePrefix;
  private final Map<DexType, DexType> emulateLibraryInterface;
  private final Map<DexString, Map<DexType, DexType>> retargetCoreLibMember;
  private final Map<DexType, DexType> backportCoreLibraryMember;
  private final Map<DexType, DexType> customConversions;
  private final List<Pair<DexType, DexString>> dontRewriteInvocation;

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  public static DesugaredLibraryConfiguration empty() {
    return new DesugaredLibraryConfiguration(
        false,
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableList.of());
  }

  public DesugaredLibraryConfiguration(
      boolean libraryCompilation,
      Map<String, String> rewritePrefix,
      Map<DexType, DexType> emulateLibraryInterface,
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember,
      Map<DexType, DexType> backportCoreLibraryMember,
      Map<DexType, DexType> customConversions,
      List<Pair<DexType, DexString>> dontRewriteInvocation) {
    this.libraryCompilation = libraryCompilation;
    this.rewritePrefix = rewritePrefix;
    this.emulateLibraryInterface = emulateLibraryInterface;
    this.retargetCoreLibMember = retargetCoreLibMember;
    this.backportCoreLibraryMember = backportCoreLibraryMember;
    this.customConversions = customConversions;
    this.dontRewriteInvocation = dontRewriteInvocation;
  }

  public PrefixRewritingMapper createPrefixRewritingMapper(DexItemFactory factory) {
    return rewritePrefix.isEmpty()
        ? PrefixRewritingMapper.empty()
        : new DesugarPrefixRewritingMapper(rewritePrefix, factory);
  }

  public boolean isLibraryCompilation() {
    return libraryCompilation;
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

  public static class Builder {

    private final DexItemFactory factory;

    private boolean libraryCompilation = false;
    private Map<String, String> rewritePrefix = new HashMap<>();
    private Map<DexType, DexType> emulateLibraryInterface = new HashMap<>();
    private Map<DexString, Map<DexType, DexType>> retargetCoreLibMember = new IdentityHashMap<>();
    private Map<DexType, DexType> backportCoreLibraryMember = new HashMap<>();
    private Map<DexType, DexType> customConversions = new HashMap<>();
    private List<Pair<DexType, DexString>> dontRewriteInvocation = new ArrayList<>();

    public Builder(DexItemFactory dexItemFactory) {
      this.factory = dexItemFactory;
    }

    public Builder setProgramCompilation() {
      libraryCompilation = false;
      return this;
    }

    public Builder setLibraryCompilation() {
      libraryCompilation = true;
      return this;
    }

    public Builder putRewritePrefix(String prefix, String rewrittenPrefix) {
      rewritePrefix.put(prefix, rewrittenPrefix);
      return this;
    }

    public Builder putEmulateLibraryInterface(
        String emulateLibraryItf, String rewrittenEmulateLibraryItf) {
      DexType interfaceType = stringClassToDexType(emulateLibraryItf);
      DexType rewrittenType = stringClassToDexType(rewrittenEmulateLibraryItf);
      emulateLibraryInterface.put(interfaceType, rewrittenType);
      return this;
    }

    public Builder putCustomConversion(String type, String conversionHolder) {
      DexType dexType = stringClassToDexType(type);
      DexType conversionType = stringClassToDexType(conversionHolder);
      customConversions.put(dexType, conversionType);
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
      typeMap.put(originalType, finalType);
      return this;
    }

    public Builder putBackportCoreLibraryMember(String backport, String rewrittenBackport) {
      DexType backportType = stringClassToDexType(backport);
      DexType rewrittenBackportType = stringClassToDexType(rewrittenBackport);
      backportCoreLibraryMember.put(backportType, rewrittenBackportType);
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

    public DesugaredLibraryConfiguration build() {
      return new DesugaredLibraryConfiguration(
          libraryCompilation,
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableMap.copyOf(emulateLibraryInterface),
          ImmutableMap.copyOf(retargetCoreLibMember),
          ImmutableMap.copyOf(backportCoreLibraryMember),
          ImmutableMap.copyOf(customConversions),
          ImmutableList.copyOf(dontRewriteInvocation));
    }
  }
}
