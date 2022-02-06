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
import java.util.IdentityHashMap;
import java.util.Map;

public class HumanToMachinePrefixConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final MachineRewritingFlags.Builder builder;
  private final String synthesizedPrefix;
  private final Map<DexType, DexType> reverse = new IdentityHashMap<>();

  public HumanToMachinePrefixConverter(
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      String synthesizedPrefix) {
    this.appInfo = appInfo;
    this.builder = builder;
    this.synthesizedPrefix = synthesizedPrefix;
  }

  private DexString toDescriptorPrefix(String prefix) {
    return appInfo
        .dexItemFactory()
        .createString("L" + DescriptorUtils.getBinaryNameFromJavaType(prefix));
  }

  public void convertPrefixFlags(HumanRewritingFlags rewritingFlags) {
    Map<DexString, DexString> descriptorPrefix = convertRewritePrefix(rewritingFlags);
    rewriteClasses(descriptorPrefix);
    rewriteValues(descriptorPrefix, rewritingFlags.getRetargetCoreLibMember());
    rewriteValues(descriptorPrefix, rewritingFlags.getCustomConversions());
    rewriteEmulatedInterface(rewritingFlags.getEmulateLibraryInterface());
    rewriteRetargetKeys(rewritingFlags.getRetargetCoreLibMember());
    rewriteReverse(descriptorPrefix);
  }

  // For custom conversions, this is responsible in rewriting backward.
  private void rewriteReverse(Map<DexString, DexString> descriptorPrefix) {
    reverse.forEach(
        (rewrittenType, type) -> {
          DexType backwardRewrittenType = rewrittenType(descriptorPrefix, rewrittenType);
          if (backwardRewrittenType != null) {
            assert backwardRewrittenType == type;
            builder.rewriteType(rewrittenType, type);
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
      Map<DexString, DexString> descriptorPrefix,
      Map<?, DexType> flags) {
    for (DexType type : flags.values()) {
      DexType rewrittenType = rewrittenType(descriptorPrefix, type);
      if (rewrittenType != null) {
        rewriteType(type, rewrittenType);
      }
    }
  }

  private void rewriteClasses(Map<DexString, DexString> descriptorPrefix) {
    for (DexClass clazz : appInfo.app().asDirect().libraryClasses()) {
      rewriteClass(descriptorPrefix, clazz);
    }
    for (DexClass clazz : appInfo.classes()) {
      rewriteClass(descriptorPrefix, clazz);
    }
  }

  private void rewriteClass(Map<DexString, DexString> descriptorPrefix, DexClass clazz) {
    DexType type = clazz.type;
    DexType rewrittenType = rewrittenType(descriptorPrefix, type);
    if (rewrittenType == null) {
      return;
    }
    rewriteType(type, rewrittenType);
  }

  private DexType rewrittenType(Map<DexString, DexString> descriptorPrefix, DexType type) {
    DexString prefixToMatch = type.descriptor.withoutArray(appInfo.dexItemFactory());
    for (DexString prefix : descriptorPrefix.keySet()) {
      if (prefixToMatch.startsWith(prefix)) {
        DexString rewrittenTypeDescriptor =
            type.descriptor.withNewPrefix(
                prefix, descriptorPrefix.get(prefix), appInfo.dexItemFactory());
        return appInfo.dexItemFactory().createType(rewrittenTypeDescriptor);
      }
    }
    return null;
  }

  private ImmutableMap<DexString, DexString> convertRewritePrefix(
      HumanRewritingFlags rewritingFlags) {
    Map<String, String> rewritePrefix = rewritingFlags.getRewritePrefix();
    ImmutableMap.Builder<DexString, DexString> mapBuilder = ImmutableMap.builder();
    for (String key : rewritePrefix.keySet()) {
      mapBuilder.put(toDescriptorPrefix(key), toDescriptorPrefix(rewritePrefix.get(key)));
    }
    return mapBuilder.build();
  }
}
