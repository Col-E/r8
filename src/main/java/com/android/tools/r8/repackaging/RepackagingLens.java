// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class RepackagingLens extends NestedGraphLens {

  private final BiMap<DexType, DexType> originalTypes;

  private RepackagingLens(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalOneToOneMap<DexField, DexField> newFieldSignatures,
      BidirectionalOneToOneMap<DexMethod, DexMethod> originalMethodSignatures,
      BiMap<DexType, DexType> originalTypes) {
    super(
        originalTypes.inverse(),
        originalMethodSignatures.getInverseOneToOneMap().getForwardMap(),
        newFieldSignatures,
        originalMethodSignatures,
        appView.graphLens(),
        appView.dexItemFactory());
    this.originalTypes = originalTypes;
  }

  @Override
  public DexType getOriginalType(DexType type) {
    DexType previous = originalTypes.getOrDefault(type, type);
    return getPrevious().getOriginalType(previous);
  }

  @Override
  public boolean isSimpleRenaming(DexType from, DexType to) {
    return originalTypes.get(to) == from || super.isSimpleRenaming(from, to);
  }

  public static class Builder implements TreeFixingCallbacks {

    protected final BiMap<DexType, DexType> originalTypes = HashBiMap.create();
    protected final MutableBidirectionalOneToOneMap<DexField, DexField> newFieldSignatures =
        new BidirectionalOneToOneHashMap<>();
    protected final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> originalMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    @Override
    public void recordMove(DexField from, DexField to) {
      newFieldSignatures.put(from, to);
    }

    @Override
    public void recordMove(DexMethod from, DexMethod to) {
      originalMethodSignatures.put(to, from);
    }

    @Override
    public void recordMove(DexType from, DexType to) {
      originalTypes.put(to, from);
    }

    public RepackagingLens build(AppView<AppInfoWithLiveness> appView) {
      assert !originalTypes.isEmpty();
      return new RepackagingLens(
          appView, newFieldSignatures, originalMethodSignatures, originalTypes);
    }
  }
}
