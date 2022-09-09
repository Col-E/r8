// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.ArgumentPermutation;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.ImmutableList;
import java.util.IdentityHashMap;
import java.util.Map;

public class ProtoNormalizerGraphLens extends NonIdentityGraphLens {

  private final BidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures;
  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges;

  ProtoNormalizerGraphLens(
      AppView<?> appView,
      BidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges) {
    super(appView);
    this.newMethodSignatures = newMethodSignatures;
    this.prototypeChanges = prototypeChanges;
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
  public DexField getRenamedFieldSignature(DexField originalField, GraphLens applied) {
    if (this == applied) {
      return originalField;
    }
    return getPrevious().getRenamedFieldSignature(originalField);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    if (this == applied) {
      return originalMethod;
    }
    return getNextMethodSignature(getPrevious().getRenamedMethodSignature(originalMethod));
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    if (this == codeLens) {
      return RewrittenPrototypeDescription.none();
    }
    DexMethod previousMethodSignature = getPreviousMethodSignature(method);
    RewrittenPrototypeDescription previousPrototypeChanges =
        getPrevious().lookupPrototypeChangesForMethodDefinition(previousMethodSignature);
    if (previousMethodSignature == method) {
      return previousPrototypeChanges;
    }
    return previousPrototypeChanges.combine(
        prototypeChanges.getOrDefault(method, RewrittenPrototypeDescription.none()));
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
    DexMethod methodSignature = previous.getReference();
    DexMethod newMethodSignature = getNextMethodSignature(methodSignature);
    if (methodSignature == newMethodSignature) {
      return previous;
    }
    assert !previous.hasReboundReference()
        || previous.getReference() == previous.getReboundReference();
    return MethodLookupResult.builder(this)
        .setPrototypeChanges(
            previous
                .getPrototypeChanges()
                .combine(
                    prototypeChanges.getOrDefault(
                        newMethodSignature, RewrittenPrototypeDescription.none())))
        .setReference(newMethodSignature)
        .setType(previous.getType())
        .build();
  }

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return previous;
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    return newMethodSignatures.getRepresentativeKeyOrDefault(method, method);
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return newMethodSignatures.getOrDefault(method, method);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges =
        new IdentityHashMap<>();

    private Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public RewrittenPrototypeDescription recordNewMethodSignature(
        DexEncodedMethod method, DexMethod newMethodSignature) {
      assert method.getReference() != newMethodSignature;
      newMethodSignatures.put(method.getReference(), newMethodSignature);
      if (!method.getParameters().equals(newMethodSignature.getParameters())) {
        RewrittenPrototypeDescription prototypeChangesForMethod =
            computePrototypeChanges(method, newMethodSignature);
        prototypeChanges.put(newMethodSignature, prototypeChangesForMethod);
        return prototypeChangesForMethod;
      }
      return RewrittenPrototypeDescription.none();
    }

    // TODO(b/195112263): Canonicalize the permutation maps.
    private static RewrittenPrototypeDescription computePrototypeChanges(
        DexEncodedMethod method, DexMethod newMethodSignature) {
      int firstNonReceiverArgumentIndex = method.getFirstNonReceiverArgumentIndex();
      int numberOfArguments = method.getNumberOfArguments();
      ArgumentPermutation.Builder argumentPermutationBuilder =
          ArgumentPermutation.builder(numberOfArguments);
      boolean[] used = new boolean[numberOfArguments];
      for (int argumentIndex = firstNonReceiverArgumentIndex;
          argumentIndex < numberOfArguments;
          argumentIndex++) {
        DexType argumentType = method.getArgumentType(argumentIndex);
        for (int newArgumentIndex = firstNonReceiverArgumentIndex;
            newArgumentIndex < numberOfArguments;
            newArgumentIndex++) {
          DexType newArgumentType =
              newMethodSignature.getArgumentType(
                  newArgumentIndex, method.getAccessFlags().isStatic());
          if (argumentType == newArgumentType && !used[newArgumentIndex]) {
            argumentPermutationBuilder.setNewArgumentIndex(argumentIndex, newArgumentIndex);
            used[newArgumentIndex] = true;
            break;
          }
        }
      }
      ArgumentPermutation argumentPermutation = argumentPermutationBuilder.build();
      assert !argumentPermutation.isDefault();
      ArgumentInfoCollection argumentInfoCollection =
          ArgumentInfoCollection.builder()
              .setArgumentInfosSize(numberOfArguments)
              .setArgumentPermutation(argumentPermutation)
              .build();
      return RewrittenPrototypeDescription.create(ImmutableList.of(), null, argumentInfoCollection);
    }

    public boolean isEmpty() {
      return newMethodSignatures.isEmpty();
    }

    public ProtoNormalizerGraphLens build() {
      return new ProtoNormalizerGraphLens(appView, newMethodSignatures, prototypeChanges);
    }
  }
}
