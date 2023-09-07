// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.distribution;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dex.VirtualFile.PackageSplitPopulator;
import com.android.tools.r8.dex.VirtualFile.VirtualFileCycler;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

public abstract class MultiStartupDexDistributor {

  StartupProfile startupProfile;

  MultiStartupDexDistributor(StartupProfile startupProfile) {
    this.startupProfile = startupProfile;
  }

  public abstract void distribute(
      List<DexProgramClass> classes,
      PackageSplitPopulator packageSplitPopulator,
      VirtualFile virtualFile,
      VirtualFileCycler virtualFileCycler);

  void distributeInOrder(
      List<DexProgramClass> classes, VirtualFile virtualFile, VirtualFileCycler virtualFileCycler) {
    // Add the startup classes one by one.
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

  boolean hasSpaceForTransaction(VirtualFile virtualFile) {
    return !virtualFile.isFull();
  }

  Reference2IntMap<DexProgramClass> computeClassMetrics(
      List<DexProgramClass> classes, ToIntFunction<DexProgramClass> fn) {
    Reference2IntMap<DexProgramClass> result = new Reference2IntOpenHashMap<>();
    result.defaultReturnValue(0);
    for (DexProgramClass clazz : classes) {
      result.put(clazz, fn.applyAsInt(clazz));
    }
    return result;
  }

  public static MultiStartupDexDistributor getDefault(StartupProfile startupProfile) {
    return new ClassByNameMultiStartupDexDistributor(startupProfile);
  }

  public static MultiStartupDexDistributor get(
      InternalOptions options, StartupProfile startupProfile) {
    String strategyName = options.getStartupOptions().getMultiStartupDexDistributionStrategyName();
    if (strategyName == null) {
      return getDefault(startupProfile);
    }
    switch (strategyName) {
      case "classByName":
        return getDefault(startupProfile);
      case "classByNumberOfStartupMethods":
        return new ClassByLowestMetricMultiStartupDexDistributor(startupProfile) {

          @Override
          int getMetric(DexEncodedMethod method) {
            return startupProfile.containsMethodRule(method.getReference()) ? -1 : 0;
          }
        };
      case "classByNumberOfStartupMethodsMinusNumberOfNonStartupMethods":
        return new ClassByLowestMetricMultiStartupDexDistributor(startupProfile) {

          @Override
          boolean forceSpillClassesWithNoStartupMethods() {
            return true;
          }

          @Override
          int getMetric(DexEncodedMethod method) {
            return startupProfile.containsMethodRule(method.getReference()) ? -1 : 1;
          }
        };
      case "packageByName":
        return new PackageByNameMultiStartupDexDistributor(startupProfile);
      case "packageByNumberOfStartupMethods":
        throw new Unimplemented();
      default:
        throw new IllegalArgumentException(
            "Unexpected multi startup dex distribution strategy: " + strategyName);
    }
  }

  private abstract static class ClassByLowestMetricMultiStartupDexDistributor
      extends MultiStartupDexDistributor {

    ClassByLowestMetricMultiStartupDexDistributor(StartupProfile startupProfile) {
      super(startupProfile);
    }

    @Override
    public void distribute(
        List<DexProgramClass> classes,
        PackageSplitPopulator packageSplitPopulator,
        VirtualFile virtualFile,
        VirtualFileCycler virtualFileCycler) {
      Reference2IntMap<DexProgramClass> classMetrics =
          computeClassMetrics(classes, this::getMetric);
      List<DexProgramClass> distribution = new ArrayList<>(classes);
      distribution.sort(
          Comparator.<DexProgramClass>comparingInt(classMetrics::getInt)
              .thenComparing(DexClass::getType));
      distributeInOrder(distribution, virtualFile, virtualFileCycler);
    }

    int getMetric(DexProgramClass clazz) {
      int metric = 0;
      boolean seenStartupMethod = false;
      for (DexEncodedMethod method : clazz.methods()) {
        metric += getMetric(method);
        seenStartupMethod |= startupProfile.containsMethodRule(method.getReference());
      }
      if (forceSpillClassesWithNoStartupMethods() && !seenStartupMethod) {
        metric = Integer.MAX_VALUE;
      }
      return metric;
    }

    boolean forceSpillClassesWithNoStartupMethods() {
      return false;
    }

    abstract int getMetric(DexEncodedMethod method);
  }

  private static class ClassByNameMultiStartupDexDistributor extends MultiStartupDexDistributor {

    ClassByNameMultiStartupDexDistributor(StartupProfile startupProfile) {
      super(startupProfile);
    }

    @Override
    public void distribute(
        List<DexProgramClass> classes,
        PackageSplitPopulator packageSplitPopulator,
        VirtualFile virtualFile,
        VirtualFileCycler virtualFileCycler) {
      // Add the (already sorted) startup classes one by one.
      distributeInOrder(classes, virtualFile, virtualFileCycler);
    }
  }

  private static class PackageByNameMultiStartupDexDistributor extends MultiStartupDexDistributor {

    PackageByNameMultiStartupDexDistributor(StartupProfile startupProfile) {
      super(startupProfile);
    }

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
