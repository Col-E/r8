// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachinePrefixConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final MachineRewritingFlags.Builder builder;
  private final String synthesizedPrefix;
  private final Map<DexString, DexString> descriptorPrefix;
  private final Map<DexType, DexType> reverse = new IdentityHashMap<>();
  private final Set<DexString> usedPrefix = Sets.newIdentityHashSet();

  public HumanToMachinePrefixConverter(
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      String synthesizedPrefix,
      Map<String, String> descriptorPrefix) {
    this.appInfo = appInfo;
    this.builder = builder;
    this.synthesizedPrefix = synthesizedPrefix;
    this.descriptorPrefix = convertRewritePrefix(descriptorPrefix);
  }

  public void convertPrefixFlags(
      HumanRewritingFlags rewritingFlags, BiConsumer<String, Set<DexString>> warnConsumer) {
    rewriteClasses();
    rewriteValues(rewritingFlags.getRetargetCoreLibMember());
    rewriteValues(rewritingFlags.getCustomConversions());
    rewriteEmulatedInterface(rewritingFlags.getEmulateLibraryInterface());
    rewriteRetargetKeys(rewritingFlags.getRetargetCoreLibMember());
    rewriteReverse();
    warnIfUnusedPrefix(warnConsumer);
  }

  private void warnIfUnusedPrefix(BiConsumer<String, Set<DexString>> warnConsumer) {
    Set<DexString> prefixes = Sets.newIdentityHashSet();
    prefixes.addAll(descriptorPrefix.keySet());
    prefixes.removeAll(usedPrefix);
    warnConsumer.accept("The following prefixes do not match any type: ", prefixes);
  }

  // For custom conversions, this is responsible in rewriting backward.
  private void rewriteReverse() {
    reverse.forEach(
        (rewrittenType, type) -> {
          DexType chainType = rewrittenType(rewrittenType);
          if (chainType != null) {
            builder.rewriteType(rewrittenType, chainType);
          }
        });
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

  private void rewriteRetargetKeys(Map<DexMethod, DexType> retarget) {
    for (DexMethod dexMethod : retarget.keySet()) {
      DexType type = convertJavaNameToDesugaredLibrary(dexMethod.holder);
      builder.rewriteDerivedTypeOnly(dexMethod.holder, type);
    }
  }

  private void rewriteEmulatedInterface(Map<DexType, DexType> emulateLibraryInterface) {
    emulateLibraryInterface.forEach(builder::rewriteDerivedTypeOnly);
  }

  private void rewriteType(DexType type, DexType rewrittenType) {
    builder.rewriteType(type, rewrittenType);
    reverse.put(rewrittenType, type);
  }

  private void rewriteValues(
      Map<?, DexType> flags) {
    for (DexType type : flags.values()) {
      registerType(type);
    }
  }

  private void rewriteClasses() {
    for (DexClass clazz : appInfo.app().asDirect().libraryClasses()) {
      rewriteClass(clazz);
    }
    for (DexClass clazz : appInfo.classes()) {
      rewriteClass(clazz);
    }
  }

  private void rewriteClass(DexClass clazz) {
    registerType(clazz.type);
    // We allow missing referenced types for the work-in-progress desugaring.
    if (clazz.superType != null) {
      registerType(clazz.superType);
    }
    clazz.interfaces.forEach(this::registerType);
    if (clazz.getInnerClasses() != null) {
      clazz.getInnerClasses().forEach(attr -> attr.forEachType(this::registerType));
    }
  }

  private void registerType(DexType type) {
    DexType rewrittenType = rewrittenType(type);
    if (rewrittenType != null) {
      rewriteType(type, rewrittenType);
    }
  }

  private DexType rewrittenType(DexType type) {
    DexString prefixToMatch = type.descriptor.withoutArray(appInfo.dexItemFactory());
    for (DexString prefix : descriptorPrefix.keySet()) {
      if (prefixToMatch.startsWith(prefix)) {
        DexString rewrittenTypeDescriptor =
            type.descriptor.withNewPrefix(
                prefix, descriptorPrefix.get(prefix), appInfo.dexItemFactory());
        usedPrefix.add(prefix);
        return appInfo.dexItemFactory().createType(rewrittenTypeDescriptor);
      }
    }
    return null;
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
