// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DesugaredLibraryConfiguration {

  // TODO(b/134732760): should use DexString, DexType, DexMethod or so on when possible.
  private final boolean libraryCompilation;
  private final Map<String, String> rewritePrefix;
  private final Map<String, String> emulateLibraryInterface;
  private final Map<String, String> retargetCoreLibMember;
  private final Map<String, String> backportCoreLibraryMember;
  private final List<String> dontRewriteInvocation;

  public static Builder builder(DexItemFactory dexItemFactory, Reporter reporter) {
    return new Builder(dexItemFactory, reporter);
  }

  public static DesugaredLibraryConfiguration empty() {
    return new DesugaredLibraryConfiguration(
        false,
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableList.of());
  }

  public DesugaredLibraryConfiguration(
      boolean libraryCompilation,
      Map<String, String> rewritePrefix,
      Map<String, String> emulateLibraryInterface,
      Map<String, String> retargetCoreLibMember,
      Map<String, String> backportCoreLibraryMember,
      List<String> dontRewriteInvocation) {
    this.libraryCompilation = libraryCompilation;
    this.rewritePrefix = rewritePrefix;
    this.emulateLibraryInterface = emulateLibraryInterface;
    this.retargetCoreLibMember = retargetCoreLibMember;
    this.backportCoreLibraryMember = backportCoreLibraryMember;
    this.dontRewriteInvocation = dontRewriteInvocation;
  }

  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  public Map<String, String> getRewritePrefix() {
    return rewritePrefix;
  }

  public Map<String, String> getEmulateLibraryInterface() {
    return emulateLibraryInterface;
  }

  public Map<String, String> getRetargetCoreLibMember() {
    return retargetCoreLibMember;
  }

  public Map<String, String> getBackportCoreLibraryMember() {
    return backportCoreLibraryMember;
  }

  public List<String> getDontRewriteInvocation() {
    return dontRewriteInvocation;
  }

  public static class Builder {

    private final DexItemFactory factory;
    private final Reporter reporter;

    private boolean libraryCompilation = false;
    private Map<String, String> rewritePrefix = new HashMap<>();
    private Map<String, String> emulateLibraryInterface = new HashMap<>();
    private Map<String, String> retargetCoreLibMember = new HashMap<>();
    private Map<String, String> backportCoreLibraryMember = new HashMap<>();
    private List<String> dontRewriteInvocation = new ArrayList<>();

    public Builder(DexItemFactory dexItemFactory, Reporter reporter) {
      this.factory = dexItemFactory;
      this.reporter = reporter;
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
      emulateLibraryInterface.put(emulateLibraryItf, rewrittenEmulateLibraryItf);
      return this;
    }

    public Builder putRetargetCoreLibMember(String retarget, String rewrittenRetarget) {
      retargetCoreLibMember.put(retarget, rewrittenRetarget);
      return this;
    }

    public Builder putBackportCoreLibraryMember(String backport, String rewrittenBackport) {
      backportCoreLibraryMember.put(backport, rewrittenBackport);
      return this;
    }

    public Builder addDontRewriteInvocation(String dontRewriteInvocation) {
      this.dontRewriteInvocation.add(dontRewriteInvocation);
      return this;
    }

    public DesugaredLibraryConfiguration build() {
      return new DesugaredLibraryConfiguration(
          libraryCompilation,
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableMap.copyOf(emulateLibraryInterface),
          ImmutableMap.copyOf(retargetCoreLibMember),
          ImmutableMap.copyOf(backportCoreLibraryMember),
          ImmutableList.copyOf(dontRewriteInvocation));
    }
  }
}
