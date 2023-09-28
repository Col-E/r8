// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.HumanToMachineSpecificationConverter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Timing;
import java.util.List;
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

  public static HumanDesugaredLibrarySpecification empty() {
    return new HumanDesugaredLibrarySpecification(
        HumanTopLevelFlags.empty(), HumanRewritingFlags.empty(), false);
  }

  @Override
  public boolean isEmpty() {
    return rewritingFlags.isEmpty();
  }

  @Override
  public boolean isHuman() {
    return true;
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

  @Override
  public String getIdentifier() {
    return topLevelFlags.getIdentifier();
  }

  @Override
  public Set<String> getMaintainTypeOrPrefixForTesting() {
    return rewritingFlags.getMaintainPrefix();
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
      DexApplication app, Timing timing) {
    return new HumanToMachineSpecificationConverter(timing).convert(this, app);
  }
}
