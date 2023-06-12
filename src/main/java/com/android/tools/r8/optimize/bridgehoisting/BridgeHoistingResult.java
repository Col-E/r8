// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.bridgehoisting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.BiConsumer;

class BridgeHoistingResult {

  private final AppView<AppInfoWithLiveness> appView;

  // Mapping from non-hoisted bridge methods to hoisted bridge methods.
  private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
      bridgeToHoistedBridgeMap = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

  BridgeHoistingResult(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void forEachHoistedBridge(BiConsumer<ProgramMethod, BridgeInfo> consumer) {
    bridgeToHoistedBridgeMap.forEachManyToOneMapping(
        (bridges, hoistedBridge) -> {
          DexProgramClass clazz = appView.definitionForProgramType(hoistedBridge.getHolderType());
          ProgramMethod method = hoistedBridge.lookupOnProgramClass(clazz);
          if (method != null) {
            consumer.accept(method, method.getDefinition().getOptimizationInfo().getBridgeInfo());
          }
        });
  }

  public boolean isEmpty() {
    return bridgeToHoistedBridgeMap.isEmpty();
  }

  public void move(Iterable<DexMethod> from, DexMethod to, DexMethod representative) {
    DexMethod originalRepresentative =
        bridgeToHoistedBridgeMap.getRepresentativeKeyOrDefault(representative, representative);
    Set<DexMethod> originalFrom = Sets.newLinkedHashSet();
    for (DexMethod method : from) {
      Set<DexMethod> keys = bridgeToHoistedBridgeMap.removeValue(method);
      if (keys.isEmpty()) {
        originalFrom.add(method);
      } else {
        originalFrom.addAll(keys);
      }
    }
    assert originalFrom.contains(originalRepresentative);
    bridgeToHoistedBridgeMap.put(originalFrom, to);
    bridgeToHoistedBridgeMap.setRepresentative(to, originalRepresentative);
  }

  public BridgeHoistingLens buildLens() {
    assert !isEmpty();
    return new BridgeHoistingLens(appView, bridgeToHoistedBridgeMap);
  }
}
