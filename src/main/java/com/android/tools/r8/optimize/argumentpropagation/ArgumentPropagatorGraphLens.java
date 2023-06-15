// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ArgumentPropagatorGraphLens extends NestedGraphLens {

  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges;

  ArgumentPropagatorGraphLens(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalOneToOneMap<DexField, DexField> fieldMap,
      BidirectionalOneToOneMap<DexMethod, DexMethod> methodMap,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges) {
    super(appView, fieldMap, methodMap, EMPTY_TYPE_MAP);
    this.prototypeChanges = prototypeChanges;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  @Override
  public boolean isArgumentPropagatorGraphLens() {
    return true;
  }

  public boolean hasPrototypeChanges(DexMethod method) {
    return prototypeChanges.containsKey(method);
  }

  public RewrittenPrototypeDescription getPrototypeChanges(DexMethod method) {
    assert hasPrototypeChanges(method);
    return prototypeChanges.getOrDefault(method, RewrittenPrototypeDescription.none());
  }


  @Override
  protected boolean isLegitimateToHaveEmptyMappings() {
    return true;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    FieldLookupResult lookupResult = super.internalDescribeLookupField(previous);
    if (lookupResult.getReference().getType() != previous.getReference().getType()) {
      return FieldLookupResult.builder(this)
          .setReboundReference(lookupResult.getReboundReference())
          .setReference(lookupResult.getReference())
          .setReadCastType(lookupResult.getReadCastType())
          .setWriteCastType(lookupResult.getReference().getType())
          .build();
    }
    return lookupResult;
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    DexMethod previous = getPreviousMethodSignature(method);
    if (!hasPrototypeChanges(method)) {
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
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, InvokeType type) {
    return hasPrototypeChanges(newMethod)
            && getPrototypeChanges(newMethod)
                .getArgumentInfoCollection()
                .isConvertedToStaticMethod()
        ? InvokeType.STATIC
        : super.mapInvocationType(newMethod, originalMethod, type);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final MutableBidirectionalOneToOneMap<DexField, DexField> newFieldSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges =
        new IdentityHashMap<>();

    Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public boolean isEmpty() {
      return newFieldSignatures.isEmpty()
          && newMethodSignatures.isEmpty()
          && prototypeChanges.isEmpty();
    }

    public ArgumentPropagatorGraphLens.Builder mergeDisjoint(
        ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder) {
      newFieldSignatures.putAll(partialGraphLensBuilder.newFieldSignatures);
      newMethodSignatures.putAll(partialGraphLensBuilder.newMethodSignatures);
      prototypeChanges.putAll(partialGraphLensBuilder.prototypeChanges);
      return this;
    }

    public Builder recordMove(DexField from, DexField to) {
      assert from != to;
      newFieldSignatures.put(from, to);
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

    public Builder recordStaticized(
        DexMethod method, RewrittenPrototypeDescription prototypeChangesForMethod) {
      prototypeChanges.put(method, prototypeChangesForMethod);
      return this;
    }

    public ArgumentPropagatorGraphLens build() {
      if (isEmpty()) {
        return null;
      }
      ArgumentPropagatorGraphLens argumentPropagatorGraphLens =
          new ArgumentPropagatorGraphLens(
              appView, newFieldSignatures, newMethodSignatures, prototypeChanges);
      fixupPrototypeChangesAfterFieldSignatureChanges(argumentPropagatorGraphLens);
      return argumentPropagatorGraphLens;
    }

    private void fixupPrototypeChangesAfterFieldSignatureChanges(
        ArgumentPropagatorGraphLens argumentPropagatorGraphLens) {
      for (Entry<DexMethod, RewrittenPrototypeDescription> entry : prototypeChanges.entrySet()) {
        RewrittenPrototypeDescription prototypeChangesForMethod = entry.getValue();
        RewrittenPrototypeDescription rewrittenPrototypeChangesForMethod =
            prototypeChangesForMethod.rewrittenWithLens(
                appView, argumentPropagatorGraphLens, argumentPropagatorGraphLens.getPrevious());
        if (rewrittenPrototypeChangesForMethod != prototypeChangesForMethod) {
          entry.setValue(rewrittenPrototypeChangesForMethod);
        }
      }
    }
  }
}
