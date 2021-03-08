// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HorizontallyMergedClasses implements MergedClasses {

  private final BidirectionalManyToOneMap<DexType, DexType> mergedClasses;

  public HorizontallyMergedClasses(BidirectionalManyToOneMap<DexType, DexType> mergedClasses) {
    this.mergedClasses = mergedClasses;
  }

  static Builder builder() {
    return new Builder();
  }

  public static HorizontallyMergedClasses empty() {
    return new HorizontallyMergedClasses(new EmptyBidirectionalOneToOneMap<>());
  }

  @Override
  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    mergedClasses.forEachManyToOneMapping(consumer);
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

  public Set<DexType> getTargets() {
    return mergedClasses.values();
  }

  @Override
  public boolean hasBeenMergedIntoDifferentType(DexType type) {
    return mergedClasses.containsKey(type);
  }

  @Override
  public boolean isMergeTarget(DexType type) {
    return mergedClasses.containsValue(type);
  }

  public boolean hasBeenMergedOrIsMergeTarget(DexType type) {
    return this.hasBeenMergedIntoDifferentType(type) || isMergeTarget(type);
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

    private final MutableBidirectionalManyToOneMap<DexType, DexType> mergedClasses =
        BidirectionalManyToOneHashMap.newIdentityHashMap();

    void addMergeGroup(MergeGroup group) {
      group.forEachSource(clazz -> mergedClasses.put(clazz.getType(), group.getTarget().getType()));
    }

    Builder addMergeGroups(Iterable<MergeGroup> groups) {
      groups.forEach(this::addMergeGroup);
      return this;
    }

    HorizontallyMergedClasses build() {
      return new HorizontallyMergedClasses(mergedClasses);
    }
  }
}
