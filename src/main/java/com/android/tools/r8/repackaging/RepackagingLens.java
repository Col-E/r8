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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class RepackagingLens extends NestedGraphLens {

  private RepackagingLens(
      AppView<AppInfoWithLiveness> appView,
      BiMap<DexField, DexField> originalFieldSignatures,
      BiMap<DexMethod, DexMethod> originalMethodSignatures,
      Map<DexType, DexType> typeMap) {
    super(
        typeMap,
        originalMethodSignatures.inverse(),
        originalFieldSignatures.inverse(),
        originalFieldSignatures,
        originalMethodSignatures,
        appView.graphLens(),
        appView.dexItemFactory());
  }

  public static class Builder {

    protected final Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    protected final BiMap<DexField, DexField> originalFieldSignatures = HashBiMap.create();
    protected final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

    public void recordMove(DexField from, DexField to) {
      originalFieldSignatures.put(to, from);
    }

    public void recordMove(DexMethod from, DexMethod to) {
      originalMethodSignatures.put(to, from);
    }

    public void recordMove(DexType from, DexType to) {
      typeMap.put(from, to);
    }

    public RepackagingLens build(AppView<AppInfoWithLiveness> appView) {
      assert !typeMap.isEmpty();
      return new RepackagingLens(
          appView, originalFieldSignatures, originalMethodSignatures, typeMap);
    }
  }
}
