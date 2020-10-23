// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class VerticallyMergedClasses implements MergedClasses {

  private final Map<DexType, DexType> mergedClasses;
  private final Map<DexType, Set<DexType>> mergedClassesInverse;

  public VerticallyMergedClasses(
      Map<DexType, DexType> mergedClasses, Map<DexType, Set<DexType>> mergedClassesInverse) {
    this.mergedClasses = mergedClasses;
    this.mergedClassesInverse = mergedClassesInverse;
  }

  public Map<DexType, DexType> getForwardMap() {
    return mergedClasses;
  }

  public Collection<DexType> getSourcesFor(DexType type) {
    return mergedClassesInverse.getOrDefault(type, Collections.emptySet());
  }

  public DexType getTargetFor(DexType type) {
    assert mergedClasses.containsKey(type);
    return mergedClasses.get(type);
  }

  public boolean hasBeenMergedIntoSubtype(DexType type) {
    return mergedClasses.containsKey(type);
  }

  public boolean isTarget(DexType type) {
    return !getSourcesFor(type).isEmpty();
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    for (Collection<DexType> sourcesForTarget : mergedClassesInverse.values()) {
      for (DexType source : sourcesForTarget) {
        assert appView.appInfo().wasPruned(source)
            : "Expected vertically merged class `" + source.toSourceString() + "` to be absent";
      }
    }
    return true;
  }

  @Override
  public boolean hasBeenMerged(DexType type) {
    return hasBeenMergedIntoSubtype(type);
  }
}
