// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.LegacyToHumanSpecificationConverter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LegacyDesugaredLibrarySpecification implements DesugaredLibrarySpecification {

  private final boolean libraryCompilation;
  private final LegacyTopLevelFlags topLevelFlags;
  private final LegacyRewritingFlags rewritingFlags;

  public LegacyDesugaredLibrarySpecification(
      LegacyTopLevelFlags topLevelFlags,
      LegacyRewritingFlags rewritingFlags,
      boolean libraryCompilation) {
    this.libraryCompilation = libraryCompilation;
    this.topLevelFlags = topLevelFlags;
    this.rewritingFlags = rewritingFlags;
  }

  @Override
  public boolean isEmpty() {
    return rewritingFlags.isEmpty();
  }

  @Override
  public boolean isLegacy() {
    return true;
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

  @Override
  public AndroidApiLevel getRequiredCompilationApiLevel() {
    return topLevelFlags.getRequiredCompilationAPILevel();
  }

  @Override
  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  @Override
  public String getSynthesizedLibraryClassesPackagePrefix() {
    return topLevelFlags.getSynthesizedLibraryClassesPackagePrefix();
  }

  @Override
  public String getIdentifier() {
    return topLevelFlags.getIdentifier();
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

  public Map<DexType, DexType> getCustomConversions() {
    return rewritingFlags.getCustomConversions();
  }

  public Set<DexType> getWrapperConversions() {
    return rewritingFlags.getWrapperConversions();
  }


  @Override
  public List<String> getExtraKeepRules() {
    return topLevelFlags.getExtraKeepRules();
  }

  @Override
  public Set<String> getMaintainTypeOrPrefixForTesting() {
    return ImmutableSet.of();
  }

  @Override
  public String getJsonSource() {
    return topLevelFlags.getJsonSource();
  }

  @Override
  public MachineDesugaredLibrarySpecification toMachineSpecification(
      DexApplication app, Timing timing) throws IOException {
    return new LegacyToHumanSpecificationConverter(timing)
        .convert(this, app)
        .toMachineSpecification(app, timing);
  }
}
