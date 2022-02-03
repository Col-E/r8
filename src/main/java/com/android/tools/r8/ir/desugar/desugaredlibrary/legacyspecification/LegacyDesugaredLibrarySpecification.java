// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LegacyDesugaredLibrarySpecification {

  private final boolean libraryCompilation;
  private final LegacyTopLevelFlags topLevelFlags;
  private final LegacyRewritingFlags rewritingFlags;

  public static LegacyDesugaredLibrarySpecification withOnlyRewritePrefixForTesting(
      Map<String, String> prefix, InternalOptions options) {
    return new LegacyDesugaredLibrarySpecification(
        LegacyTopLevelFlags.empty(),
        LegacyRewritingFlags.withOnlyRewritePrefixForTesting(prefix, options),
        true);
  }

  public static LegacyDesugaredLibrarySpecification empty() {
    return new LegacyDesugaredLibrarySpecification(
        LegacyTopLevelFlags.empty(), LegacyRewritingFlags.empty(), false) {

      @Override
      public boolean isEmptyConfiguration() {
        return true;
      }
    };
  }

  public LegacyDesugaredLibrarySpecification(
      LegacyTopLevelFlags topLevelFlags,
      LegacyRewritingFlags rewritingFlags,
      boolean libraryCompilation) {
    this.libraryCompilation = libraryCompilation;
    this.topLevelFlags = topLevelFlags;
    this.rewritingFlags = rewritingFlags;
  }

  public LegacyTopLevelFlags getTopLevelFlags() {
    return topLevelFlags;
  }

  public LegacyRewritingFlags getRewritingFlags() {
    return rewritingFlags;
  }

  public boolean supportAllCallbacksFromLibrary() {
    return topLevelFlags.supportAllCallbacksFromLibrary();
  }

  public AndroidApiLevel getRequiredCompilationApiLevel() {
    return topLevelFlags.getRequiredCompilationAPILevel();
  }

  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  public String getSynthesizedLibraryClassesPackagePrefix() {
    return topLevelFlags.getSynthesizedLibraryClassesPackagePrefix();
  }

  // TODO(b/183918843): We are currently computing a new name for the class by replacing the
  //  initial package prefix by the synthesized library class package prefix, it would be better
  //  to make the rewriting explicit in the desugared library json file.
  public String convertJavaNameToDesugaredLibrary(DexType type) {
    String prefix =
        DescriptorUtils.getJavaTypeFromBinaryName(getSynthesizedLibraryClassesPackagePrefix());
    String interfaceType = type.toString();
    int firstPackage = interfaceType.indexOf('.');
    return prefix + interfaceType.substring(firstPackage + 1);
  }

  public String getIdentifier() {
    return topLevelFlags.getIdentifier();
  }

  public Map<String, String> getRewritePrefix() {
    return rewritingFlags.getRewritePrefix();
  }

  public boolean hasEmulatedLibraryInterfaces() {
    return !getEmulateLibraryInterface().isEmpty();
  }

  public Map<DexType, DexType> getEmulateLibraryInterface() {
    return rewritingFlags.getEmulateLibraryInterface();
  }

  // If the method is retargeted, answers the retargeted method, else null.
  public DexMethod retargetMethod(DexEncodedMethod method, AppView<?> appView) {
    Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
        rewritingFlags.getRetargetCoreLibMember();
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

  public DexMethod retargetMethod(DexClassAndMethod method, AppView<?> appView) {
    return retargetMethod(method.getDefinition(), appView);
  }

  public Map<DexString, Map<DexType, DexType>> getRetargetCoreLibMember() {
    return rewritingFlags.getRetargetCoreLibMember();
  }

  public Map<DexType, DexType> getBackportCoreLibraryMember() {
    return rewritingFlags.getBackportCoreLibraryMember();
  }

  public Map<DexType, DexType> getCustomConversions() {
    return rewritingFlags.getCustomConversions();
  }

  public Set<DexType> getWrapperConversions() {
    return rewritingFlags.getWrapperConversions();
  }

  public List<Pair<DexType, DexString>> getDontRewriteInvocation() {
    return rewritingFlags.getDontRewriteInvocation();
  }

  public Set<DexType> getDontRetargetLibMember() {
    return rewritingFlags.getDontRetargetLibMember();
  }

  public List<String> getExtraKeepRules() {
    return topLevelFlags.getExtraKeepRules();
  }

  public String getJsonSource() {
    return topLevelFlags.getJsonSource();
  }

  public boolean isEmptyConfiguration() {
    return false;
  }
}
