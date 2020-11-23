// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HorizontallyMergedLambdaClasses implements MergedClasses {

  private final BidirectionalManyToOneMap<DexType, DexType> mergedClasses;

  public HorizontallyMergedLambdaClasses(Map<DexType, LambdaGroup> lambdas) {
    this.mergedClasses = new BidirectionalManyToOneMap<>();
    lambdas.forEach((lambda, group) -> mergedClasses.put(lambda, group.getGroupClassType()));
  }

  public static HorizontallyMergedLambdaClasses empty() {
    return new HorizontallyMergedLambdaClasses(Collections.emptyMap());
  }

  @Override
  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    mergedClasses.forEach(consumer);
  }

  @Override
  public boolean hasBeenMergedIntoDifferentType(DexType type) {
    return mergedClasses.containsKey(type);
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
}
