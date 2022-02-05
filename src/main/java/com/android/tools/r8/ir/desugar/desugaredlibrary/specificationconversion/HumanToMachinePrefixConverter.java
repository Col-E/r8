// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class HumanToMachinePrefixConverter {

  private final AppInfoWithClassHierarchy appInfo;

  public HumanToMachinePrefixConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  private DexString toDescriptorPrefix(String prefix) {
    return appInfo
        .dexItemFactory()
        .createString("L" + DescriptorUtils.getBinaryNameFromJavaType(prefix));
  }

  public void convertPrefixFlags(
      HumanRewritingFlags rewritingFlags,
      MachineRewritingFlags.Builder builder,
      String synthesizedPrefix) {
    Map<DexString, DexString> descriptorPrefix = convertRewritePrefix(rewritingFlags);
    rewriteClasses(descriptorPrefix, builder);
    rewriteValues(descriptorPrefix, builder, rewritingFlags.getRetargetCoreLibMember());
    rewriteValues(descriptorPrefix, builder, rewritingFlags.getCustomConversions());
    rewriteEmulatedInterface(builder, rewritingFlags.getEmulateLibraryInterface());
    rewriteRetargetKeys(builder, rewritingFlags.getRetargetCoreLibMember(), synthesizedPrefix);
  }

  public DexType convertJavaNameToDesugaredLibrary(DexType type, String prefix) {
    String convertedPrefix = DescriptorUtils.getJavaTypeFromBinaryName(prefix);
    String interfaceType = type.toString();
    int firstPackage = interfaceType.indexOf('.');
    return appInfo
        .dexItemFactory()
        .createType(
            DescriptorUtils.javaTypeToDescriptor(
                convertedPrefix + interfaceType.substring(firstPackage + 1)));
  }

  private void rewriteRetargetKeys(
      MachineRewritingFlags.Builder builder, Map<DexMethod, DexType> retarget, String prefix) {
    for (DexMethod dexMethod : retarget.keySet()) {
      DexType type = convertJavaNameToDesugaredLibrary(dexMethod.holder, prefix);
      builder.rewriteDerivedTypeOnly(dexMethod.holder, type);
    }
  }

  private void rewriteEmulatedInterface(
      MachineRewritingFlags.Builder builder, Map<DexType, DexType> emulateLibraryInterface) {
    emulateLibraryInterface.forEach(builder::rewriteDerivedTypeOnly);
  }

  private void rewriteValues(
      Map<DexString, DexString> descriptorPrefix,
      MachineRewritingFlags.Builder builder,
      Map<?, DexType> flags) {
    for (DexType type : flags.values()) {
      DexType rewrittenType = rewrittenType(descriptorPrefix, type);
      if (rewrittenType != null) {
        builder.rewriteType(type, rewrittenType);
      }
    }
  }

  private void rewriteClasses(
      Map<DexString, DexString> descriptorPrefix, MachineRewritingFlags.Builder builder) {
    for (DexProgramClass clazz : appInfo.classes()) {
      DexType type = clazz.type;
      DexType rewrittenType = rewrittenType(descriptorPrefix, type);
      if (rewrittenType != null) {
        builder.rewriteType(type, rewrittenType);
      }
    }
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
