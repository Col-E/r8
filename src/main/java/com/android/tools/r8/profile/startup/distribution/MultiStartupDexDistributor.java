// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.distribution;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dex.VirtualFile.PackageSplitPopulator;
import com.android.tools.r8.dex.VirtualFile.VirtualFileCycler;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;

public abstract class MultiStartupDexDistributor {

  public abstract void distribute(
      List<DexProgramClass> classes,
      PackageSplitPopulator packageSplitPopulator,
      VirtualFile virtualFile,
      VirtualFileCycler virtualFileCycler);

  boolean hasSpaceForTransaction(VirtualFile virtualFile) {
    return !virtualFile.isFull();
  }

  public static MultiStartupDexDistributor getDefault() {
    return new ClassByNameMultiStartupDexDistributor();
  }

  public static MultiStartupDexDistributor get(InternalOptions options) {
    String strategyName = options.getStartupOptions().getMultiStartupDexDistributionStrategyName();
    if (strategyName == null) {
      return getDefault();
    }
    switch (strategyName) {
      case "classByName":
        return getDefault();
      case "classByNumberOfStartupMethods":
        throw new Unimplemented();
      case "classByNumberOfStartupMethodsMinusNumberOfNonStartupMethods":
        throw new Unimplemented();
      case "packageByName":
        return new PackageByNameMultiStartupDexDistributor();
      case "packageByNumberOfStartupMethods":
        throw new Unimplemented();
      default:
        throw new IllegalArgumentException(
            "Unexpected multi startup dex distribution strategy: " + strategyName);
    }
  }

  private static class ClassByNameMultiStartupDexDistributor extends MultiStartupDexDistributor {

    @Override
    public void distribute(
        List<DexProgramClass> classes,
        PackageSplitPopulator packageSplitPopulator,
        VirtualFile virtualFile,
        VirtualFileCycler virtualFileCycler) {
      // Add the (already sorted) startup classes one by one.
      for (DexProgramClass startupClass : classes) {
        virtualFile.addClass(startupClass);
        if (hasSpaceForTransaction(virtualFile)) {
          virtualFile.commitTransaction();
        } else {
          virtualFile.abortTransaction();
          virtualFile = virtualFileCycler.addFile();
          virtualFile.addClass(startupClass);
          assert hasSpaceForTransaction(virtualFile);
          virtualFile.commitTransaction();
        }
      }
    }
  }

  private static class PackageByNameMultiStartupDexDistributor extends MultiStartupDexDistributor {

    @Override
    public void distribute(
        List<DexProgramClass> classes,
        PackageSplitPopulator packageSplitPopulator,
        VirtualFile virtualFile,
        VirtualFileCycler virtualFileCycler) {
      virtualFileCycler.restart();
      packageSplitPopulator.distributeClasses(classes);
    }
  }
}
