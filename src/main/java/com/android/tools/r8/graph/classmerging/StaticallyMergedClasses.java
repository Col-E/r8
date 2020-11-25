// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import java.util.Set;
import java.util.function.BiConsumer;

public class StaticallyMergedClasses implements MergedClasses {

  private final BidirectionalManyToOneMap<DexType, DexType> mergedClasses;

  public StaticallyMergedClasses(BidirectionalManyToOneMap<DexType, DexType> mergedClasses) {
    this.mergedClasses = mergedClasses;
  }

  public static StaticallyMergedClasses empty() {
    return new StaticallyMergedClasses(new EmptyBidirectionalOneToOneMap<>());
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    mergedClasses.forEachManyToOneMapping(consumer);
  }

  @Override
  public boolean hasBeenMergedIntoDifferentType(DexType type) {
    return false;
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    return true;
  }

  public static class Builder {

    private final MutableBidirectionalManyToOneMap<DexType, DexType> mergedClasses =
        new BidirectionalManyToOneHashMap<>();

    private Builder() {}

    public void recordMerge(DexProgramClass source, DexProgramClass target) {
      for (DexType previousSource : mergedClasses.removeValue(source.getType())) {
        mergedClasses.put(previousSource, target.getType());
      }
      mergedClasses.put(source.getType(), target.getType());
    }

    public StaticallyMergedClasses build() {
      return new StaticallyMergedClasses(mergedClasses);
    }
  }
}
