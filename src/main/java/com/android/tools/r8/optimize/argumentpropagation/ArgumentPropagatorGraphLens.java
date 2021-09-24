// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.NestedGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class ArgumentPropagatorGraphLens extends NestedGraphLens {

  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges;

  ArgumentPropagatorGraphLens(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalOneToOneMap<DexMethod, DexMethod> methodMap,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges) {
    super(appView, EMPTY_FIELD_MAP, methodMap, EMPTY_TYPE_MAP);
    this.prototypeChanges = prototypeChanges;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  public RewrittenPrototypeDescription getPrototypeChanges(DexMethod method) {
    assert method != internalGetPreviousMethodSignature(method);
    return prototypeChanges.getOrDefault(method, RewrittenPrototypeDescription.none());
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    DexMethod previous = internalGetPreviousMethodSignature(method);
    if (previous == method) {
      assert !this.prototypeChanges.containsKey(method);
      return prototypeChanges;
    }
    RewrittenPrototypeDescription newPrototypeChanges =
        prototypeChanges.combine(getPrototypeChanges(method));
    assert previous.getReturnType().isVoidType()
        || !method.getReturnType().isVoidType()
        || newPrototypeChanges.hasRewrittenReturnInfo();
    return newPrototypeChanges;
  }

  @Override
  public DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    return super.internalGetPreviousMethodSignature(method);
  }

  @Override
  public DexMethod internalGetNextMethodSignature(DexMethod method) {
    return super.internalGetNextMethodSignature(method);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges =
        new IdentityHashMap<>();

    Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public boolean isEmpty() {
      return newMethodSignatures.isEmpty();
    }

    public ArgumentPropagatorGraphLens.Builder mergeDisjoint(
        ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder) {
      newMethodSignatures.putAll(partialGraphLensBuilder.newMethodSignatures);
      prototypeChanges.putAll(partialGraphLensBuilder.prototypeChanges);
      return this;
    }

    public Builder recordMove(
        DexMethod from, DexMethod to, RewrittenPrototypeDescription prototypeChangesForMethod) {
      assert from != to;
      newMethodSignatures.put(from, to);
      if (!prototypeChangesForMethod.isEmpty()) {
        prototypeChanges.put(to, prototypeChangesForMethod);
      }
      assert from.getReturnType().isVoidType()
          || !to.getReturnType().isVoidType()
          || prototypeChangesForMethod.hasRewrittenReturnInfo();
      return this;
    }

    public ArgumentPropagatorGraphLens build() {
      return isEmpty()
          ? null
          : new ArgumentPropagatorGraphLens(appView, newMethodSignatures, prototypeChanges);
    }
  }
}
