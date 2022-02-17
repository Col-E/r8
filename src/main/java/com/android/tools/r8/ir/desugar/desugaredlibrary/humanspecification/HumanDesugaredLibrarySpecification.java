// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.HumanToMachineSpecificationConverter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HumanDesugaredLibrarySpecification implements DesugaredLibrarySpecification {

  private final boolean libraryCompilation;
  private final HumanTopLevelFlags topLevelFlags;
  private final HumanRewritingFlags rewritingFlags;

  public HumanDesugaredLibrarySpecification(
      HumanTopLevelFlags topLevelFlags,
      HumanRewritingFlags rewritingFlags,
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
  public boolean isHuman() {
    return true;
  }

  @Override
  public HumanDesugaredLibrarySpecification asHumanDesugaredLibrarySpecification() {
    return this;
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

  public HumanTopLevelFlags getTopLevelFlags() {
    return topLevelFlags;
  }

  public HumanRewritingFlags getRewritingFlags() {
    return rewritingFlags;
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
    return rewritingFlags.getEmulatedInterfaces();
  }

  // If the method is retargeted, answers the retargeted method, else null.
  public DexMethod retargetMethod(DexEncodedMethod method, AppView<?> appView) {
    Map<DexMethod, DexType> retargetCoreLibMember = rewritingFlags.getRetargetMethod();
    DexType dexType = retargetCoreLibMember.get(method.getReference());
    if (dexType != null) {
      return appView
          .dexItemFactory()
          .createMethod(
              dexType,
              appView.dexItemFactory().prependHolderToProto(method.getReference()),
              method.getName());
    }
    return null;
  }

  public DexMethod retargetMethod(DexClassAndMethod method, AppView<?> appView) {
    return retargetMethod(method.getDefinition(), appView);
  }

  public Map<DexMethod, DexType> getRetargetCoreLibMember() {
    return rewritingFlags.getRetargetMethod();
  }

  public Map<DexType, DexType> getBackportCoreLibraryMember() {
    return rewritingFlags.getLegacyBackport();
  }

  public Map<DexType, DexType> getCustomConversions() {
    return rewritingFlags.getCustomConversions();
  }

  public Set<DexType> getWrapperConversions() {
    return rewritingFlags.getWrapperConversions();
  }

  public Set<DexMethod> getDontRewriteInvocation() {
    return rewritingFlags.getDontRewriteInvocation();
  }

  public Set<DexType> getDontRetargetLibMember() {
    return rewritingFlags.getDontRetarget();
  }

  @Override
  public List<String> getExtraKeepRules() {
    return topLevelFlags.getExtraKeepRules();
  }

  @Override
  public String getJsonSource() {
    return topLevelFlags.getJsonSource();
  }

  public boolean isEmptyConfiguration() {
    return rewritingFlags.isEmpty();
  }

  @Override
  public MachineDesugaredLibrarySpecification toMachineSpecification(
      InternalOptions options, AndroidApp app) throws IOException {
    return new HumanToMachineSpecificationConverter()
        .convert(
            this,
            isLibraryCompilation() ? app.getProgramResourceProviders() : null,
            app.getLibraryResourceProviders(),
            options);
  }

  @Override
  public MachineDesugaredLibrarySpecification toMachineSpecification(
      InternalOptions options, Path library, Path desugaredJDKLib) throws IOException {
    return new HumanToMachineSpecificationConverter()
        .convert(this, isLibraryCompilation() ? desugaredJDKLib : null, library, options);
  }
}
