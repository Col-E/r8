// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class LambdaDesugaringLens extends NonIdentityGraphLens {

  private final BidirectionalOneToOneMap<DexMethod, DexMethod> originalMethodSignatures;

  private LambdaDesugaringLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      BidirectionalOneToOneMap<DexMethod, DexMethod> originalMethodSignatures) {
    super(appView);
    this.originalMethodSignatures = originalMethodSignatures;
  }

  public static Builder createBuilder() {
    return new Builder();
  }

  public void forEachForcefullyMovedLambdaMethod(Consumer<DexMethod> consumer) {
    originalMethodSignatures.forEachValue(consumer);
  }

  public void forEachForcefullyMovedLambdaMethod(BiConsumer<DexMethod, DexMethod> consumer) {
    originalMethodSignatures.forEach((to, from) -> consumer.accept(from, to));
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
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    return getPrevious().getOriginalMethodSignature(internalGetPreviousMethodSignature(method));
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField) {
    return originalField;
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    return originalMethod;
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    return getPrevious()
        .lookupPrototypeChangesForMethodDefinition(internalGetPreviousMethodSignature(method));
  }

  @Override
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
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

  @Override
  protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    return originalMethodSignatures.getRepresentativeValueOrDefault(method, method);
  }

  public static class Builder implements ForcefullyMovedLambdaMethodConsumer {

    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> originalMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    private Builder() {}

    @Override
    public void acceptForcefullyMovedLambdaMethod(DexMethod from, DexMethod to) {
      originalMethodSignatures.put(to, from);
    }

    public LambdaDesugaringLens build(AppView<? extends AppInfoWithClassHierarchy> appView) {
      if (!originalMethodSignatures.isEmpty()) {
        return new LambdaDesugaringLens(appView, originalMethodSignatures);
      }
      return null;
    }
  }
}
