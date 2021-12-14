// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class HumanTopLevelFlags {

  private final AndroidApiLevel requiredCompilationAPILevel;
  private final String synthesizedLibraryClassesPackagePrefix;
  private final String identifier;
  private final String jsonSource;
  // Setting supportAllCallbacksFromLibrary reduces the number of generated call-backs,
  // more specifically:
  // - no call-back is generated for emulated interface method overrides (forEach, etc.)
  // - no call-back is generated inside the desugared library itself.
  // Such setting decreases significantly the desugared library dex file, but virtual calls from
  // within the library to desugared library classes instances as receiver may be incorrect, for
  // example the method forEach in Iterable may be executed over a concrete implementation.
  private final boolean supportAllCallbacksFromLibrary;
  private final List<String> extraKeepRules;

  HumanTopLevelFlags(
      AndroidApiLevel requiredCompilationAPILevel,
      String synthesizedLibraryClassesPackagePrefix,
      String identifier,
      String jsonSource,
      boolean supportAllCallbacksFromLibrary,
      List<String> extraKeepRules) {
    this.requiredCompilationAPILevel = requiredCompilationAPILevel;
    this.synthesizedLibraryClassesPackagePrefix = synthesizedLibraryClassesPackagePrefix;
    this.identifier = identifier;
    this.jsonSource = jsonSource;
    this.supportAllCallbacksFromLibrary = supportAllCallbacksFromLibrary;
    this.extraKeepRules = extraKeepRules;
  }

  public static HumanTopLevelFlags empty() {
    return new HumanTopLevelFlags(
        AndroidApiLevel.B, "unused", null, null, true, ImmutableList.of());
  }

  public static HumanTopLevelFlags testing() {
    return new HumanTopLevelFlags(
        AndroidApiLevel.B, "unused", "testing", null, true, ImmutableList.of());
  }

  public static Builder builder() {
    return new Builder();
  }

  public AndroidApiLevel getRequiredCompilationAPILevel() {
    return requiredCompilationAPILevel;
  }

  public String getSynthesizedLibraryClassesPackagePrefix() {
    return synthesizedLibraryClassesPackagePrefix;
  }

  public String getIdentifier() {
    return identifier;
  }

  public String getJsonSource() {
    return jsonSource;
  }

  public boolean supportAllCallbacksFromLibrary() {
    return supportAllCallbacksFromLibrary;
  }

  public List<String> getExtraKeepRules() {
    return extraKeepRules;
  }

  public static class Builder {

    private AndroidApiLevel requiredCompilationAPILevel;
    private String synthesizedLibraryClassesPackagePrefix;
    private String identifier;
    private String jsonSource;
    private Boolean supportAllCallbacksFromLibrary;
    private List<String> extraKeepRules;

    Builder() {}

    public Builder setRequiredCompilationAPILevel(AndroidApiLevel requiredCompilationAPILevel) {
      this.requiredCompilationAPILevel = requiredCompilationAPILevel;
      return this;
    }

    public Builder setSynthesizedLibraryClassesPackagePrefix(String prefix) {
      this.synthesizedLibraryClassesPackagePrefix = prefix.replace('.', '/');
      return this;
    }

    public Builder setDesugaredLibraryIdentifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder setJsonSource(String jsonSource) {
      this.jsonSource = jsonSource;
      return this;
    }

    public Builder setSupportAllCallbacksFromLibrary(boolean supportAllCallbacksFromLibrary) {
      this.supportAllCallbacksFromLibrary = supportAllCallbacksFromLibrary;
      return this;
    }

    public Builder setExtraKeepRules(List<String> rules) {
      extraKeepRules = rules;
      return this;
    }

    public HumanTopLevelFlags build() {
      assert synthesizedLibraryClassesPackagePrefix != null;
      assert supportAllCallbacksFromLibrary != null;
      return new HumanTopLevelFlags(
          requiredCompilationAPILevel,
          synthesizedLibraryClassesPackagePrefix,
          identifier,
          jsonSource,
          supportAllCallbacksFromLibrary,
          extraKeepRules);
    }
  }
}
