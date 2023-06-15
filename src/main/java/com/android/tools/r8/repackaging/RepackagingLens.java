// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.Map;

public class RepackagingLens extends NestedGraphLens {

  private final BidirectionalOneToOneMap<DexType, DexType> newTypes;
  private final Map<String, String> packageRenamings;

  private RepackagingLens(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalOneToOneMap<DexField, DexField> newFieldSignatures,
      BidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures,
      BidirectionalOneToOneMap<DexType, DexType> newTypes,
      Map<String, String> packageRenamings) {
    super(appView, newFieldSignatures, newMethodSignatures, newTypes);
    this.newTypes = newTypes;
    this.packageRenamings = packageRenamings;
  }

  @Override
  public String lookupPackageName(String pkg) {
    return packageRenamings.getOrDefault(getPrevious().lookupPackageName(pkg), pkg);
  }

  @Override
  public <T extends DexReference> boolean isSimpleRenaming(T from, T to) {
    if (from == to) {
      assert false : "The from and to references should not be equal";
      return false;
    }
    if (super.isSimpleRenaming(from, to)) {
      // Repackaging only move classes and therefore if a previous lens has a simple renaming it
      // will be maintained here.
      return true;
    }
    return DexReference.applyPair(
        from,
        to,
        this::isSimpleTypeRenamingOrEqual,
        this::isSimpleTypeRenamingOrEqual,
        this::isSimpleTypeRenamingOrEqual);
  }

  @Override
  public boolean isSimpleRenamingLens() {
    return true;
  }

  private boolean isSimpleTypeRenamingOrEqual(DexType from, DexType to) {
    return from == to || newTypes.get(from) == to;
  }

  private boolean isSimpleTypeRenamingOrEqual(DexMember<?, ?> from, DexMember<?, ?> to) {
    if (!isSimpleTypeRenamingOrEqual(from.getHolderType(), to.getHolderType())) {
      return false;
    }
    return IterableUtils.testPairs(
        this::isSimpleTypeRenamingOrEqual,
        from.getReferencedBaseTypes(dexItemFactory()),
        to.getReferencedBaseTypes(dexItemFactory()));
  }

  public static class Builder {

    protected final MutableBidirectionalOneToOneMap<DexType, DexType> newTypes =
        new BidirectionalOneToOneHashMap<>();
    protected final MutableBidirectionalOneToOneMap<DexField, DexField> newFieldSignatures =
        new BidirectionalOneToOneHashMap<>();
    protected final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    public void recordMove(DexField from, DexField to) {
      newFieldSignatures.put(from, to);
    }

    public void recordMove(DexMethod from, DexMethod to) {
      newMethodSignatures.put(from, to);
    }

    public void recordMove(DexType from, DexType to) {
      newTypes.put(from, to);
    }

    public RepackagingLens build(
        AppView<AppInfoWithLiveness> appView, Map<String, String> packageRenamings) {
      assert !newTypes.isEmpty();
      return new RepackagingLens(
          appView, newFieldSignatures, newMethodSignatures, newTypes, packageRenamings);
    }
  }
}
