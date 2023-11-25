// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags.HumanEmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachinePrefixConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final MachineRewritingFlags.Builder builder;
  private final String synthesizedPrefix;
  private final boolean libraryCompilation;
  private final Map<DexString, DexString> descriptorPrefix;
  private final Set<DexString> descriptorMaintainPrefix;
  private final Set<DexString> descriptorDontRewritePrefix;
  private final Map<DexString, Map<DexString, DexString>> descriptorDifferentPrefix;
  private final Set<DexString> usedPrefix = Sets.newIdentityHashSet();
  private final boolean jdk11Legacy;

  public HumanToMachinePrefixConverter(
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      String synthesizedPrefix,
      boolean libraryCompilation,
      String identifier,
      HumanRewritingFlags rewritingFlags) {
    this.appInfo = appInfo;
    this.builder = builder;
    this.synthesizedPrefix = synthesizedPrefix;
    this.libraryCompilation = libraryCompilation;
    this.descriptorPrefix = convertRewritePrefix(rewritingFlags.getRewritePrefix());
    this.descriptorDontRewritePrefix = convertPrefixSet(rewritingFlags.getDontRewritePrefix());
    this.descriptorMaintainPrefix = convertPrefixSet(rewritingFlags.getMaintainPrefix());
    this.descriptorDifferentPrefix =
        convertRewriteDifferentPrefix(rewritingFlags.getRewriteDerivedPrefix());
    this.jdk11Legacy = identifier.startsWith("com.tools.android:desugar_jdk_libs:1.2.");
  }

  public void convertPrefixFlags(
      HumanRewritingFlags rewritingFlags, BiConsumer<String, Set<DexString>> warnConsumer) {
    rewriteClasses();
    rewriteValues(rewritingFlags.getRetargetMethodToType());
    rewriteValues(rewritingFlags.getRetargetMethodEmulatedDispatchToType());
    rewriteValues(rewritingFlags.getCustomConversions());
    rewriteMethodValues(rewritingFlags.getRetargetMethodToMethod());
    rewriteMethodValues(rewritingFlags.getRetargetMethodEmulatedDispatchToMethod());
    rewriteEmulatedInterface(rewritingFlags.getEmulatedInterfaces());
    rewriteRetargetKeys(rewritingFlags.getRetargetMethodEmulatedDispatchToType());
    rewriteRetargetKeys(rewritingFlags.getRetargetMethodEmulatedDispatchToMethod());
    rewriteApiConversions(rewritingFlags.getApiGenericConversion());
    warnIfUnusedPrefix(warnConsumer);
  }

  private void warnIfUnusedPrefix(BiConsumer<String, Set<DexString>> warnConsumer) {
    Set<DexString> unused = Sets.newIdentityHashSet();
    unused.addAll(descriptorPrefix.keySet());
    unused.addAll(descriptorMaintainPrefix);
    unused.addAll(descriptorDifferentPrefix.keySet());
    unused.removeAll(usedPrefix);
    if (jdk11Legacy) {
      // We have to allow duplicate because of the jdk11 legacy configuration, since there is no
      // api_greater_or_equal in legacy, we're bound to have duplicates.
      // If we have x.y. -> w.z and x. -> w., then if one is used the other is used.
      List<DexString> duplicates = new ArrayList<>();
      for (DexString prefix : unused) {
        descriptorPrefix.forEach(
            (k, v) -> {
              if (!unused.contains(k) && (k.startsWith(prefix) || prefix.startsWith(k))) {
                duplicates.add(prefix);
              }
            });
      }
      duplicates.forEach(unused::remove);
    }
    warnConsumer.accept("The following prefixes do not match any type: ", unused);
  }

  public DexType convertJavaNameToDesugaredLibrary(DexType type) {
    String convertedPrefix = DescriptorUtils.getJavaTypeFromBinaryName(synthesizedPrefix);
    String interfaceType = type.toString();
    int firstPackage = interfaceType.indexOf('.');
    return appInfo
        .dexItemFactory()
        .createType(
            DescriptorUtils.javaTypeToDescriptor(
                convertedPrefix + interfaceType.substring(firstPackage + 1)));
  }

  private void rewriteRetargetKeys(Map<DexMethod, ?> retarget) {
    for (DexMethod dexMethod : retarget.keySet()) {
      DexType type = convertJavaNameToDesugaredLibrary(dexMethod.holder);
      builder.rewriteDerivedTypeOnly(dexMethod.holder, type);
    }
  }

  private void rewriteApiConversions(Map<DexMethod, DexMethod[]> apiGenericConversions) {
    apiGenericConversions.forEach(
        (k, v) -> {
          for (DexMethod dexMethod : v) {
            if (dexMethod != null) {
              registerType(dexMethod.getHolderType());
            }
          }
        });
  }

  private void rewriteEmulatedInterface(
      Map<DexType, HumanEmulatedInterfaceDescriptor> emulateLibraryInterface) {
    emulateLibraryInterface.forEach(
        (k, v) -> builder.rewriteDerivedTypeOnly(k, v.getRewrittenType()));
  }

  private void rewriteValues(
      Map<?, DexType> flags) {
    for (DexType type : flags.values()) {
      registerType(type);
    }
  }

  private void rewriteMethodValues(Map<?, DexMethod> flags) {
    for (DexMethod method : flags.values()) {
      registerType(method.getHolderType());
    }
  }

  private void rewriteClasses() {
    appInfo.app().forEachLibraryType(this::registerClassType);
    if (libraryCompilation) {
      appInfo.app().forEachProgramType(this::registerClassType);
    }
  }

  private void registerClassType(DexType type) {
    registerType(type);
    registerMaintainType(type);
    registerDifferentType(type);
  }

  private void registerType(DexType type) {
    DexType rewrittenType = rewrittenType(type);
    if (rewrittenType != null) {
      if (prefixMatching(type, descriptorDontRewritePrefix) != null) {
        return;
      }
      builder.rewriteType(type, rewrittenType);
    }
  }

  private void registerMaintainType(DexType type) {
    DexString prefix = prefixMatching(type, descriptorMaintainPrefix);
    if (prefix == null) {
      return;
    }
    builder.maintainType(type);
    // We cannot rely on the synthetic classes being on the same package anyway since the runtime
    // may use a class from the Android framework which is considered in a different package even
    // if the package name is identical.
    // This is required since the Android framework also generates external synthetics for lambdas
    // that are publicly visible.
    builder.rewriteDerivedTypeOnly(type, convertJavaNameToDesugaredLibrary(type));
    usedPrefix.add(prefix);
  }

  private void registerDifferentType(DexType type) {
    DexString prefix = prefixMatching(type, descriptorDifferentPrefix.keySet());
    if (prefix == null) {
      return;
    }
    descriptorDifferentPrefix
        .get(prefix)
        .forEach(
            (k, v) -> {
              DexString typeDescriptor =
                  type.descriptor.withNewPrefix(prefix, k, appInfo.dexItemFactory());
              DexString rewrittenTypeDescriptor =
                  type.descriptor.withNewPrefix(prefix, v, appInfo.dexItemFactory());
              DexType newKey = appInfo.dexItemFactory().createType(typeDescriptor);
              assert appInfo.definitionForWithoutExistenceAssert(newKey) == null
                  : "Trying to rewrite a type "
                      + newKey
                      + " with different prefix that already exists.";
              builder.rewriteType(
                  newKey, appInfo.dexItemFactory().createType(rewrittenTypeDescriptor));
            });
    usedPrefix.add(prefix);
  }

  private DexString prefixMatching(DexType type, Set<DexString> prefixes) {
    DexString prefixToMatch = type.descriptor.withoutArray(appInfo.dexItemFactory());
    for (DexString prefix : prefixes) {
      if (prefixToMatch.startsWith(prefix)) {
        return prefix;
      }
    }
    return null;
  }

  private DexType rewrittenType(DexType type) {
    DexString prefix = prefixMatching(type, descriptorPrefix.keySet());
    if (prefix == null) {
      return null;
    }
    DexString rewrittenTypeDescriptor =
        type.descriptor.withNewPrefix(
            prefix, descriptorPrefix.get(prefix), appInfo.dexItemFactory());
    usedPrefix.add(prefix);
    return appInfo.dexItemFactory().createType(rewrittenTypeDescriptor);
  }

  private ImmutableMap<DexString, Map<DexString, DexString>> convertRewriteDifferentPrefix(
      Map<String, Map<String, String>> rewriteDerivedPrefix) {
    ImmutableMap.Builder<DexString, Map<DexString, DexString>> mapBuilder = ImmutableMap.builder();
    for (String key : rewriteDerivedPrefix.keySet()) {
      mapBuilder.put(toDescriptorPrefix(key), convertRewritePrefix(rewriteDerivedPrefix.get(key)));
    }
    return mapBuilder.build();
  }

  private ImmutableSet<DexString> convertPrefixSet(Set<String> maintainPrefix) {
    ImmutableSet.Builder<DexString> builder = ImmutableSet.builder();
    for (String prefix : maintainPrefix) {
      builder.add(toDescriptorPrefix(prefix));
    }
    return builder.build();
  }

  private ImmutableMap<DexString, DexString> convertRewritePrefix(
      Map<String, String> rewritePrefix) {
    ImmutableMap.Builder<DexString, DexString> mapBuilder = ImmutableMap.builder();
    for (String key : rewritePrefix.keySet()) {
      mapBuilder.put(toDescriptorPrefix(key), toDescriptorPrefix(rewritePrefix.get(key)));
    }
    return mapBuilder.build();
  }

  private DexString toDescriptorPrefix(String prefix) {
    return appInfo
        .dexItemFactory()
        .createString("L" + DescriptorUtils.getBinaryNameFromJavaType(prefix));
  }
}
