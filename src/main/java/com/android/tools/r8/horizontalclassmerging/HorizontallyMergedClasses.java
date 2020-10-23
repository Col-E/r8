// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class HorizontallyMergedClasses implements MergedClasses {

  private final BidirectionalManyToOneMap<DexType, DexType> mergedClasses;

  public HorizontallyMergedClasses(BidirectionalManyToOneMap<DexType, DexType> mergedClasses) {
    this.mergedClasses = mergedClasses;
  }

  public static HorizontallyMergedClasses empty() {
    return new HorizontallyMergedClasses(new BidirectionalManyToOneMap<>());
  }

  public DexType getMergeTargetOrDefault(DexType type) {
    return mergedClasses.getOrDefault(type, type);
  }

  public Set<DexType> getSources() {
    return mergedClasses.keySet();
  }

  public Set<DexType> getSourcesFor(DexType type) {
    return mergedClasses.getKeys(type);
  }

  public boolean hasBeenMergedIntoDifferentType(DexType type) {
    return mergedClasses.hasKey(type);
  }

  @Override
  public boolean hasBeenMerged(DexType type) {
    return hasBeenMergedIntoDifferentType(type);
  }

  public boolean isMergeTarget(DexType type) {
    return mergedClasses.hasValue(type);
  }

  public boolean hasBeenMergedOrIsMergeTarget(DexType type) {
    return hasBeenMerged(type) || isMergeTarget(type);
  }

  Map<DexType, DexType> getForwardMap() {
    return mergedClasses.getForwardMap();
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    for (DexType source : mergedClasses.keySet()) {
      assert appView.appInfo().wasPruned(source)
          : "Expected horizontally merged lambda class `"
              + source.toSourceString()
              + "` to be absent";
    }
    return true;
  }

  public static class Builder {
    private final BidirectionalManyToOneMap<DexType, DexType> mergedClasses =
        new BidirectionalManyToOneMap<>();

    public HorizontallyMergedClasses build() {
      return new HorizontallyMergedClasses(mergedClasses);
    }

    public void addMergeGroup(DexProgramClass target, Collection<DexProgramClass> toMergeGroup) {
      for (DexProgramClass clazz : toMergeGroup) {
        mergedClasses.put(clazz.type, target.type);
      }
    }
  }
}
