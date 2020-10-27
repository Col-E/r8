// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.PrefixRewritingMapper.DesugarPrefixRewritingMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DesugaredLibraryConfiguration {
  public static final String FALL_BACK_SYNTHESIZED_CLASSES_PACKAGE_PREFIX = "j$/";
  public static final boolean FALL_BACK_SUPPORT_ALL_CALLBACKS_FROM_LIBRARY = true;
  public static final DesugaredLibraryConfiguration EMPTY_DESUGARED_LIBRARY_CONFIGURATION =
      new DesugaredLibraryConfiguration(
          AndroidApiLevel.B,
          false,
          FALL_BACK_SYNTHESIZED_CLASSES_PACKAGE_PREFIX,
          null,
          null,
          FALL_BACK_SUPPORT_ALL_CALLBACKS_FROM_LIBRARY,
          ImmutableMap.of(),
          ImmutableMap.of(),
          ImmutableMap.of(),
          ImmutableMap.of(),
          ImmutableMap.of(),
          ImmutableSet.of(),
          ImmutableList.of(),
          ImmutableList.of());
  private final AndroidApiLevel requiredCompilationAPILevel;
  private final boolean libraryCompilation;
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
  public final boolean supportAllCallbacksFromLibrary;
  private final Map<String, String> rewritePrefix;
  private final Map<DexType, DexType> emulateLibraryInterface;
  private final Map<DexString, Map<DexType, DexType>> retargetCoreLibMember;
  private final Map<DexType, DexType> backportCoreLibraryMember;
  private final Map<DexType, DexType> customConversions;
  private final List<Pair<DexType, DexString>> dontRewriteInvocation;
  private final List<String> extraKeepRules;
  private final Set<DexType> wrapperConversions;

  public static Builder builder(DexItemFactory dexItemFactory, Reporter reporter, Origin origin) {
    return new Builder(dexItemFactory, reporter, origin);
  }

  public static DesugaredLibraryConfiguration withOnlyRewritePrefixForTesting(
      Map<String, String> prefix) {
    return new DesugaredLibraryConfiguration(
        AndroidApiLevel.B,
        true,
        FALL_BACK_SYNTHESIZED_CLASSES_PACKAGE_PREFIX,
        "testingOnlyVersion",
        null,
        FALL_BACK_SUPPORT_ALL_CALLBACKS_FROM_LIBRARY,
        prefix,
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static DesugaredLibraryConfiguration empty() {
    return EMPTY_DESUGARED_LIBRARY_CONFIGURATION;
  }

  private DesugaredLibraryConfiguration(
      AndroidApiLevel requiredCompilationAPILevel,
      boolean libraryCompilation,
      String packagePrefix,
      String identifier,
      String jsonSource,
      boolean supportAllCallbacksFromLibrary,
      Map<String, String> rewritePrefix,
      Map<DexType, DexType> emulateLibraryInterface,
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember,
      Map<DexType, DexType> backportCoreLibraryMember,
      Map<DexType, DexType> customConversions,
      Set<DexType> wrapperConversions,
      List<Pair<DexType, DexString>> dontRewriteInvocation,
      List<String> extraKeepRules) {
    this.requiredCompilationAPILevel = requiredCompilationAPILevel;
    this.libraryCompilation = libraryCompilation;
    this.synthesizedLibraryClassesPackagePrefix = packagePrefix;
    this.identifier = identifier;
    this.jsonSource = jsonSource;
    this.supportAllCallbacksFromLibrary = supportAllCallbacksFromLibrary;
    this.rewritePrefix = rewritePrefix;
    this.emulateLibraryInterface = emulateLibraryInterface;
    this.retargetCoreLibMember = retargetCoreLibMember;
    this.backportCoreLibraryMember = backportCoreLibraryMember;
    this.customConversions = customConversions;
    this.wrapperConversions = wrapperConversions;
    this.dontRewriteInvocation = dontRewriteInvocation;
    this.extraKeepRules = extraKeepRules;
  }

  public PrefixRewritingMapper createPrefixRewritingMapper(InternalOptions options) {
    return rewritePrefix.isEmpty()
        ? PrefixRewritingMapper.empty()
        : new DesugarPrefixRewritingMapper(rewritePrefix, options);
  }

  public AndroidApiLevel getRequiredCompilationApiLevel() {
    return requiredCompilationAPILevel;
  }

  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  public String getSynthesizedLibraryClassesPackagePrefix() {
    return synthesizedLibraryClassesPackagePrefix;
  }

  public String getIdentifier() {
    return identifier;
  }

  public Map<String, String> getRewritePrefix() {
    return rewritePrefix;
  }

  public Map<DexType, DexType> getEmulateLibraryInterface() {
    return emulateLibraryInterface;
  }
  // If the method is retargeted, answers the retargeted method, else null.
  public DexMethod retargetMethod(DexEncodedMethod method, AppView<?> appView) {
    Map<DexType, DexType> typeMap = retargetCoreLibMember.get(method.getName());
    if (typeMap != null && typeMap.containsKey(method.getHolderType())) {
      return appView
          .dexItemFactory()
          .createMethod(
              typeMap.get(method.getHolderType()),
              appView.dexItemFactory().prependHolderToProto(method.getReference()),
              method.getName());
    }
    return null;
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

  public Set<DexType> getWrapperConversions() {
    return wrapperConversions;
  }

  public List<Pair<DexType, DexString>> getDontRewriteInvocation() {
    return dontRewriteInvocation;
  }

  public List<String> getExtraKeepRules() {
    return extraKeepRules;
  }

  public String getJsonSource() {
    return jsonSource;
  }

  public static class Builder {
    private final DexItemFactory factory;
    private final Reporter reporter;
    private final Origin origin;
    private AndroidApiLevel requiredCompilationAPILevel;
    private boolean libraryCompilation = false;
    private String synthesizedLibraryClassesPackagePrefix =
        FALL_BACK_SYNTHESIZED_CLASSES_PACKAGE_PREFIX;
    private String identifier;
    private String jsonSource;
    private Map<String, String> rewritePrefix = new HashMap<>();
    private Map<DexType, DexType> emulateLibraryInterface = new IdentityHashMap<>();
    private Map<DexString, Map<DexType, DexType>> retargetCoreLibMember = new IdentityHashMap<>();
    private Map<DexType, DexType> backportCoreLibraryMember = new IdentityHashMap<>();
    private Map<DexType, DexType> customConversions = new IdentityHashMap<>();
    private Set<DexType> wrapperConversions = Sets.newIdentityHashSet();
    private List<Pair<DexType, DexString>> dontRewriteInvocation = new ArrayList<>();
    private List<String> extraKeepRules = Collections.emptyList();
    private boolean supportAllCallbacksFromLibrary = FALL_BACK_SUPPORT_ALL_CALLBACKS_FROM_LIBRARY;

    private Builder(DexItemFactory dexItemFactory, Reporter reporter, Origin origin) {
      this.factory = dexItemFactory;
      this.reporter = reporter;
      this.origin = origin;
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

    public Builder setRequiredCompilationAPILevel(AndroidApiLevel requiredCompilationAPILevel) {
      this.requiredCompilationAPILevel = requiredCompilationAPILevel;
      return this;
    }

    public Builder setProgramCompilation() {
      libraryCompilation = false;
      return this;
    }

    public Builder setLibraryCompilation() {
      libraryCompilation = true;
      return this;
    }

    public Builder setExtraKeepRules(List<String> rules) {
      extraKeepRules = rules;
      return this;
    }

    public Builder putRewritePrefix(String prefix, String rewrittenPrefix) {
      put(
          rewritePrefix,
          prefix,
          rewrittenPrefix,
          DesugaredLibraryConfigurationParser.REWRITE_PREFIX_KEY);
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
          DesugaredLibraryConfigurationParser.EMULATE_INTERFACE_KEY);
      return this;
    }

    public Builder putCustomConversion(String type, String conversionHolder) {
      DexType dexType = stringClassToDexType(type);
      DexType conversionType = stringClassToDexType(conversionHolder);
      put(
          customConversions,
          dexType,
          conversionType,
          DesugaredLibraryConfigurationParser.CUSTOM_CONVERSION_KEY);
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
          DesugaredLibraryConfigurationParser.RETARGET_LIB_MEMBER_KEY);
      return this;
    }

    public Builder putBackportCoreLibraryMember(String backport, String rewrittenBackport) {
      DexType backportType = stringClassToDexType(backport);
      DexType rewrittenBackportType = stringClassToDexType(rewrittenBackport);
      put(
          backportCoreLibraryMember,
          backportType,
          rewrittenBackportType,
          DesugaredLibraryConfigurationParser.BACKPORT_KEY);
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

    public void setSupportAllCallbacksFromLibrary(boolean supportAllCallbacksFromLibrary) {
      this.supportAllCallbacksFromLibrary = supportAllCallbacksFromLibrary;
    }

    public DesugaredLibraryConfiguration build() {
      validate();
      return new DesugaredLibraryConfiguration(
          requiredCompilationAPILevel,
          libraryCompilation,
          synthesizedLibraryClassesPackagePrefix,
          identifier,
          jsonSource,
          supportAllCallbacksFromLibrary,
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableMap.copyOf(emulateLibraryInterface),
          ImmutableMap.copyOf(retargetCoreLibMember),
          ImmutableMap.copyOf(backportCoreLibraryMember),
          ImmutableMap.copyOf(customConversions),
          ImmutableSet.copyOf(wrapperConversions),
          ImmutableList.copyOf(dontRewriteInvocation),
          ImmutableList.copyOf(extraKeepRules));
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
