// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ArgumentPropagatorGraphLens extends NonIdentityGraphLens {

  ArgumentPropagatorGraphLens(AppView<AppInfoWithLiveness> appView) {
    super(appView);
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
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
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    return getPrevious().getOriginalMethodSignature(method);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    return applied != this
        ? getPrevious().getRenamedMethodSignature(originalMethod, applied)
        : originalMethod;
  }

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return previous;
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
  protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    return method;
  }

  @Override
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    return getPrevious().lookupPrototypeChangesForMethodDefinition(method);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;

    Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public ArgumentPropagatorGraphLens.Builder mergeDisjoint(
        ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder) {
      // TODO(b/190154391): Implement.
      return this;
    }

    public ArgumentPropagatorGraphLens build() {
      // TODO(b/190154391): Implement.
      return null;
    }
  }
}
