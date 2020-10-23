// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.bridgehoisting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import java.util.Set;

class BridgeHoistingLens extends NonIdentityGraphLens {

  // Mapping from non-hoisted bridge methods to hoisted bridge methods.
  private final BidirectionalManyToOneMap<DexMethod, DexMethod> bridgeToHoistedBridgeMap;

  public BridgeHoistingLens(
      AppView<?> appView,
      BidirectionalManyToOneMap<DexMethod, DexMethod> bridgeToHoistedBridgeMap) {
    super(appView.dexItemFactory(), appView.graphLens());
    this.bridgeToHoistedBridgeMap = bridgeToHoistedBridgeMap;
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    return getPrevious().getOriginalMethodSignature(internalGetPreviousMethodSignature(method));
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    DexMethod renamedMethod = getPrevious().getRenamedMethodSignature(originalMethod, applied);
    return bridgeToHoistedBridgeMap.getOrDefault(renamedMethod, renamedMethod);
  }

  @Override
  protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    Set<DexMethod> bridges = bridgeToHoistedBridgeMap.getKeys(method);
    return bridges.isEmpty() ? method : bridges.iterator().next();
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return getPrevious().getOriginalType(type);
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    return getPrevious().getOriginalTypes(type);
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    return getPrevious().getOriginalFieldSignature(field);
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField) {
    return getPrevious().getRenamedFieldSignature(originalField);
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    return getPrevious().lookupPrototypeChangesForMethodDefinition(method);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
  }

  @Override
  public boolean hasCodeRewritings() {
    return getPrevious().hasCodeRewritings();
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    return previous;
  }

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    return previous;
  }

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return previous;
  }
}
