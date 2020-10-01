// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.bridgehoisting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import java.util.Set;
import java.util.function.BiConsumer;

class BridgeHoistingResult {

  private final AppView<AppInfoWithLiveness> appView;

  // Mapping from non-hoisted bridge methods to hoisted bridge methods.
  private final BidirectionalManyToOneMap<DexMethod, DexMethod> bridgeToHoistedBridgeMap =
      new BidirectionalManyToOneMap<>();

  // Mapping from non-hoisted bridge methods to the set of contexts in which they are accessed.
  private final MethodAccessInfoCollection.IdentityBuilder bridgeMethodAccessInfoCollectionBuilder =
      MethodAccessInfoCollection.identityBuilder();

  BridgeHoistingResult(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void forEachHoistedBridge(BiConsumer<ProgramMethod, BridgeInfo> consumer) {
    bridgeToHoistedBridgeMap.forEach(
        (bridges, hoistedBridge) -> {
          DexProgramClass clazz = appView.definitionForProgramType(hoistedBridge.getHolderType());
          ProgramMethod method = hoistedBridge.lookupOnProgramClass(clazz);
          if (method != null) {
            consumer.accept(method, method.getDefinition().getOptimizationInfo().getBridgeInfo());
          }
        });
  }

  public MethodAccessInfoCollection getBridgeMethodAccessInfoCollection() {
    return bridgeMethodAccessInfoCollectionBuilder.build();
  }

  public boolean isEmpty() {
    return bridgeToHoistedBridgeMap.isEmpty();
  }

  public void move(DexMethod from, DexMethod to) {
    Set<DexMethod> keys = bridgeToHoistedBridgeMap.getKeys(from);
    if (keys.isEmpty()) {
      bridgeToHoistedBridgeMap.put(from, to);
    } else {
      for (DexMethod original : keys) {
        bridgeToHoistedBridgeMap.put(original, to);
      }
    }

    MethodAccessInfoCollection methodAccessInfoCollection =
        appView.appInfo().getMethodAccessInfoCollection();
    methodAccessInfoCollection.forEachVirtualInvokeContext(
        from,
        context ->
            bridgeMethodAccessInfoCollectionBuilder.registerInvokeVirtualInContext(from, context));
  }

  public BridgeHoistingLens buildLens() {
    assert !isEmpty();
    return new BridgeHoistingLens(appView, bridgeToHoistedBridgeMap);
  }
}
