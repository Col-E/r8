// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.bridgehoisting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import java.util.Set;

class BridgeHoistingLens extends DefaultNonIdentityGraphLens {

  // Mapping from non-hoisted bridge methods to hoisted bridge methods.
  private final BidirectionalManyToOneMap<DexMethod, DexMethod> bridgeToHoistedBridgeMap;

  public BridgeHoistingLens(
      AppView<?> appView,
      BidirectionalManyToOneMap<DexMethod, DexMethod> bridgeToHoistedBridgeMap) {
    super(appView.dexItemFactory(), appView.graphLens());
    this.bridgeToHoistedBridgeMap = bridgeToHoistedBridgeMap;
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    Set<DexMethod> bridges = bridgeToHoistedBridgeMap.getKeys(method);
    return bridges.isEmpty() ? method : bridges.iterator().next();
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return bridgeToHoistedBridgeMap.getOrDefault(method, method);
  }

  @Override
  public boolean hasCodeRewritings() {
    return getPrevious().hasCodeRewritings();
  }
}
