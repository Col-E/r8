// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

// This graph lens is instantiated during vertical class merging. The graph lens is context
// sensitive in the enclosing class of a given invoke *and* the type of the invoke (e.g., invoke-
// super vs invoke-virtual). This is illustrated by the following example.
//
// public class A {
//   public void m() { ... }
// }
// public class B extends A {
//   @Override
//   public void m() { invoke-super A.m(); ... }
//
//   public void m2() { invoke-virtual A.m(); ... }
// }
//
// Vertical class merging will merge class A into class B. Since class B already has a method with
// the signature "void B.m()", the method A.m will be given a fresh name and moved to class B.
// During this process, the method corresponding to A.m will be made private such that it can be
// called via an invoke-direct instruction.
//
// For the invocation "invoke-super A.m()" in B.m, this graph lens will return the newly created,
// private method corresponding to A.m (that is now in B.m with a fresh name), such that the
// invocation will hit the same implementation as the original super.m() call.
//
// For the invocation "invoke-virtual A.m()" in B.m2, this graph lens will return the method B.m.
public class VerticalClassMergerGraphLens extends NestedGraphLens {

  interface GraphLensLookupResultProvider {

    MethodLookupResult get(RewrittenPrototypeDescription prototypeChanges);
  }

  private final AppView<?> appView;

  private VerticallyMergedClasses mergedClasses;
  private final Map<DexType, Map<DexMethod, GraphLensLookupResultProvider>>
      contextualVirtualToDirectMethodMaps;
  private Set<DexMethod> mergedMethods;
  private final Map<DexMethod, DexMethod> originalMethodSignaturesForBridges;
  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges;

  private VerticalClassMergerGraphLens(
      AppView<?> appView,
      VerticallyMergedClasses mergedClasses,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      Set<DexMethod> mergedMethods,
      Map<DexType, Map<DexMethod, GraphLensLookupResultProvider>>
          contextualVirtualToDirectMethodMaps,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> newMethodSignatures,
      Map<DexMethod, DexMethod> originalMethodSignaturesForBridges,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges) {
    super(appView, fieldMap, methodMap, mergedClasses.getBidirectionalMap(), newMethodSignatures);
    this.appView = appView;
    this.mergedClasses = mergedClasses;
    this.contextualVirtualToDirectMethodMaps = contextualVirtualToDirectMethodMaps;
    this.mergedMethods = mergedMethods;
    this.originalMethodSignaturesForBridges = originalMethodSignaturesForBridges;
    this.prototypeChanges = prototypeChanges;
  }

  @Override
  public boolean isVerticalClassMergerLens() {
    return true;
  }

  @Override
  public DexType getPreviousClassType(DexType type) {
    return type;
  }

  @Override
  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    Collection<DexType> originalTypes = mergedClasses.getSourcesFor(previous);
    Iterable<DexType> currentType = IterableUtils.singleton(previous);
    if (originalTypes == null) {
      return currentType;
    }
    return Iterables.concat(currentType, originalTypes);
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert context != null || verifyIsContextFreeForMethod(previous.getReference(), codeLens);
    assert context == null || previous.getType() != null;
    if (previous.getType() == InvokeType.SUPER && !mergedMethods.contains(context)) {
      Map<DexMethod, GraphLensLookupResultProvider> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.get(context.getHolderType());
      if (virtualToDirectMethodMap != null) {
        GraphLensLookupResultProvider result =
            virtualToDirectMethodMap.get(previous.getReference());
        if (result != null) {
          // If the super class A of the enclosing class B (i.e., context.holder())
          // has been merged into B during vertical class merging, and this invoke-super instruction
          // was resolving to a method in A, then the target method has been changed to a direct
          // method and moved into B, so that we need to use an invoke-direct instruction instead of
          // invoke-super (or invoke-static, if the method was originally a default interface
          // method).
          return result.get(previous.getPrototypeChanges());
        }
      }
    }
    MethodLookupResult lookupResult;
    DexMethod newMethod = methodMap.apply(previous.getReference());
    if (newMethod == null) {
      lookupResult = previous;
    } else {
      lookupResult =
          MethodLookupResult.builder(this)
              .setReference(newMethod)
              .setPrototypeChanges(
                  internalDescribePrototypeChanges(previous.getPrototypeChanges(), newMethod))
              .setType(mapInvocationType(newMethod, previous.getReference(), previous.getType()))
              .build();
    }
    assert !appView.testing().enableVerticalClassMergerLensAssertion
        || Streams.stream(lookupResult.getReference().getReferencedBaseTypes(dexItemFactory()))
            .noneMatch(type -> mergedClasses.hasBeenMergedIntoSubtype(type));
    return lookupResult;
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    return prototypeChanges.combine(
        this.prototypeChanges.getOrDefault(method, RewrittenPrototypeDescription.none()));
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    return super.getPreviousMethodSignature(
        originalMethodSignaturesForBridges.getOrDefault(method, method));
  }

  @Override
  public DexMethod getPreviousMethodSignatureForMapping(DexMethod method) {
    DexMethod orDefault = newMethodSignatures.getRepresentativeKeyOrDefault(method, method);
    return super.getPreviousMethodSignature(orDefault);
  }

  @Override
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, InvokeType type) {
    return mapVirtualInterfaceInvocationTypes(appView, newMethod, originalMethod, type);
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    return contextualVirtualToDirectMethodMaps.isEmpty()
        && getPrevious().isContextFreeForMethods(codeLens);
  }

  @Override
  public boolean verifyIsContextFreeForMethod(DexMethod method, GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    assert getPrevious().verifyIsContextFreeForMethod(method, codeLens);
    DexMethod previous = getPrevious().lookupMethod(method, null, null, codeLens).getReference();
    assert contextualVirtualToDirectMethodMaps.values().stream()
        .noneMatch(virtualToDirectMethodMap -> virtualToDirectMethodMap.containsKey(previous));
    return true;
  }

  public static class Builder {

    private final DexItemFactory dexItemFactory;

    protected final MutableBidirectionalOneToOneMap<DexField, DexField> fieldMap =
        new BidirectionalOneToOneHashMap<>();
    protected final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();
    private final ImmutableSet.Builder<DexMethod> mergedMethodsBuilder = ImmutableSet.builder();
    private final Map<DexType, Map<DexMethod, GraphLensLookupResultProvider>>
        contextualVirtualToDirectMethodMaps = new IdentityHashMap<>();

    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
        newMethodSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final Map<DexMethod, DexMethod> originalMethodSignaturesForBridges =
        new IdentityHashMap<>();
    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges =
        new IdentityHashMap<>();

    private final Map<DexProto, DexProto> cache = new IdentityHashMap<>();

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @SuppressWarnings("ReferenceEquality")
    static Builder createBuilderForFixup(Builder builder, VerticallyMergedClasses mergedClasses) {
      Builder newBuilder = new Builder(builder.dexItemFactory);
      builder.fieldMap.forEach(
          (key, value) ->
              newBuilder.map(
                  key, builder.getFieldSignatureAfterClassMerging(value, mergedClasses)));
      for (Map.Entry<DexMethod, DexMethod> entry : builder.methodMap.entrySet()) {
        newBuilder.map(
            entry.getKey(),
            builder.getMethodSignatureAfterClassMerging(entry.getValue(), mergedClasses));
      }
      for (DexMethod method : builder.mergedMethodsBuilder.build()) {
        newBuilder.markMethodAsMerged(
            builder.getMethodSignatureAfterClassMerging(method, mergedClasses));
      }
      for (Map.Entry<DexType, Map<DexMethod, GraphLensLookupResultProvider>> entry :
          builder.contextualVirtualToDirectMethodMaps.entrySet()) {
        DexType context = entry.getKey();
        assert context == builder.getTypeAfterClassMerging(context, mergedClasses);
        for (Map.Entry<DexMethod, GraphLensLookupResultProvider> innerEntry :
            entry.getValue().entrySet()) {
          DexMethod from = innerEntry.getKey();
          MethodLookupResult rewriting =
              innerEntry.getValue().get(RewrittenPrototypeDescription.none());
          DexMethod to =
              builder.getMethodSignatureAfterClassMerging(rewriting.getReference(), mergedClasses);
          newBuilder.mapVirtualMethodToDirectInType(
              from,
              prototypeChanges ->
                  new MethodLookupResult(to, null, rewriting.getType(), prototypeChanges),
              context);
        }
      }
      builder.newMethodSignatures.forEachManyToOneMapping(
          (originalMethodSignatures, renamedMethodSignature, representative) -> {
            DexMethod methodSignatureAfterClassMerging =
                builder.getMethodSignatureAfterClassMerging(renamedMethodSignature, mergedClasses);
            newBuilder.newMethodSignatures.put(
                originalMethodSignatures, methodSignatureAfterClassMerging);
            if (originalMethodSignatures.size() > 1) {
              newBuilder.newMethodSignatures.setRepresentative(
                  methodSignatureAfterClassMerging, representative);
            }
          });
      for (Map.Entry<DexMethod, DexMethod> entry :
          builder.originalMethodSignaturesForBridges.entrySet()) {
        newBuilder.recordCreationOfBridgeMethod(
            entry.getValue(),
            builder.getMethodSignatureAfterClassMerging(entry.getKey(), mergedClasses));
      }
      builder.prototypeChanges.forEach(
          (method, prototypeChangesForMethod) ->
              newBuilder.prototypeChanges.put(
                  builder.getMethodSignatureAfterClassMerging(method, mergedClasses),
                  prototypeChangesForMethod));
      return newBuilder;
    }

    public VerticalClassMergerGraphLens build(
        AppView<?> appView, VerticallyMergedClasses mergedClasses) {
      if (mergedClasses.isEmpty()) {
        return null;
      }
      // Build new graph lens.
      return new VerticalClassMergerGraphLens(
          appView,
          mergedClasses,
          fieldMap,
          methodMap,
          mergedMethodsBuilder.build(),
          contextualVirtualToDirectMethodMaps,
          newMethodSignatures,
          originalMethodSignaturesForBridges,
          prototypeChanges);
    }

    @SuppressWarnings("ReferenceEquality")
    private DexField getFieldSignatureAfterClassMerging(
        DexField field, VerticallyMergedClasses mergedClasses) {
      assert !field.holder.isArrayType();

      DexType holder = field.holder;
      DexType newHolder = mergedClasses.getTargetForOrDefault(holder, holder);

      DexType type = field.type;
      DexType newType = getTypeAfterClassMerging(type, mergedClasses);

      if (holder == newHolder && type == newType) {
        return field;
      }
      return dexItemFactory.createField(newHolder, newType, field.name);
    }

    @SuppressWarnings("ReferenceEquality")
    private DexMethod getMethodSignatureAfterClassMerging(
        DexMethod signature, VerticallyMergedClasses mergedClasses) {
      assert !signature.holder.isArrayType();

      DexType holder = signature.holder;
      DexType newHolder = mergedClasses.getTargetForOrDefault(holder, holder);

      DexProto proto = signature.proto;
      DexProto newProto =
          dexItemFactory.applyClassMappingToProto(
              proto, type -> getTypeAfterClassMerging(type, mergedClasses), cache);

      if (holder == newHolder && proto == newProto) {
        return signature;
      }
      return dexItemFactory.createMethod(newHolder, newProto, signature.name);
    }

    @SuppressWarnings("ReferenceEquality")
    private DexType getTypeAfterClassMerging(DexType type, VerticallyMergedClasses mergedClasses) {
      if (type.isArrayType()) {
        DexType baseType = type.toBaseType(dexItemFactory);
        DexType newBaseType = mergedClasses.getTargetForOrDefault(baseType, baseType);
        if (newBaseType != baseType) {
          return type.replaceBaseType(newBaseType, dexItemFactory);
        }
        return type;
      }
      return mergedClasses.getTargetForOrDefault(type, type);
    }

    public boolean hasMappingForSignatureInContext(DexProgramClass context, DexMethod signature) {
      Map<DexMethod, GraphLensLookupResultProvider> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.get(context.type);
      if (virtualToDirectMethodMap != null) {
        return virtualToDirectMethodMap.containsKey(signature);
      }
      return false;
    }

    public boolean hasOriginalSignatureMappingFor(DexField field) {
      return fieldMap.containsValue(field);
    }

    public boolean hasOriginalSignatureMappingFor(DexMethod method) {
      return newMethodSignatures.containsValue(method)
          || originalMethodSignaturesForBridges.containsKey(method);
    }

    public void markMethodAsMerged(DexMethod method) {
      mergedMethodsBuilder.add(method);
    }

    public void map(DexField from, DexField to) {
      fieldMap.put(from, to);
    }

    public Builder map(DexMethod from, DexMethod to) {
      methodMap.put(from, to);
      return this;
    }

    public void recordMerge(DexMethod from, DexMethod to) {
      newMethodSignatures.put(from, to);
      newMethodSignatures.put(to, to);
      newMethodSignatures.setRepresentative(to, to);
    }

    public void recordMove(DexMethod from, DexMethod to) {
      recordMove(from, to, false);
    }

    public void recordMove(DexMethod from, DexMethod to, boolean isStaticized) {
      newMethodSignatures.put(from, to);
      if (isStaticized) {
        RewrittenPrototypeDescription prototypeChangesForMethod =
            RewrittenPrototypeDescription.create(
                ImmutableList.of(),
                null,
                ArgumentInfoCollection.builder()
                    .setArgumentInfosSize(to.getParameters().size())
                    .setIsConvertedToStaticMethod()
                    .build());
        prototypeChanges.put(to, prototypeChangesForMethod);
      }
    }

    public void recordCreationOfBridgeMethod(DexMethod from, DexMethod to) {
      originalMethodSignaturesForBridges.put(to, from);
    }

    public void mapVirtualMethodToDirectInType(
        DexMethod from, GraphLensLookupResultProvider to, DexType type) {
      Map<DexMethod, GraphLensLookupResultProvider> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.computeIfAbsent(type, key -> new IdentityHashMap<>());
      virtualToDirectMethodMap.put(from, to);
    }

    @SuppressWarnings("ReferenceEquality")
    public void merge(VerticalClassMergerGraphLens.Builder builder) {
      fieldMap.putAll(builder.fieldMap);
      methodMap.putAll(builder.methodMap);
      mergedMethodsBuilder.addAll(builder.mergedMethodsBuilder.build());
      builder.newMethodSignatures.forEachManyToOneMapping(
          (keys, value, representative) -> {
            boolean isRemapping =
                Iterables.any(keys, key -> newMethodSignatures.containsValue(key) && key != value);
            if (isRemapping) {
              // If I and J are merged into A and both I.m() and J.m() exists, then we may map J.m()
              // to I.m() as a result of merging J into A, and then subsequently merge I.m() to
              // A.m() as a result of merging I into A.
              assert keys.size() == 1;
              DexMethod key = keys.iterator().next();

              // When merging J.m() to I.m() we create the mappings {I.m(), J.m()} -> I.m().
              DexMethod originalRepresentative = newMethodSignatures.getRepresentativeKey(key);
              Set<DexMethod> originalKeys = newMethodSignatures.removeValue(key);
              assert originalKeys.contains(key);

              // Now that I.m() is merged to A.m(), we modify the existing mappings into
              // {I.m(), J.m()} -> A.m().
              newMethodSignatures.put(originalKeys, value);
              newMethodSignatures.setRepresentative(value, originalRepresentative);
            } else {
              if (newMethodSignatures.containsValue(value)
                  && !newMethodSignatures.hasExplicitRepresentativeKey(value)) {
                newMethodSignatures.setRepresentative(
                    value, newMethodSignatures.getRepresentativeKey(value));
              }
              newMethodSignatures.put(keys, value);
              if (keys.size() > 1 && !newMethodSignatures.hasExplicitRepresentativeKey(value)) {
                newMethodSignatures.setRepresentative(value, representative);
              }
            }
          });
      prototypeChanges.putAll(builder.prototypeChanges);
      originalMethodSignaturesForBridges.putAll(builder.originalMethodSignaturesForBridges);
      for (DexType context : builder.contextualVirtualToDirectMethodMaps.keySet()) {
        Map<DexMethod, GraphLensLookupResultProvider> current =
            contextualVirtualToDirectMethodMaps.get(context);
        Map<DexMethod, GraphLensLookupResultProvider> other =
            builder.contextualVirtualToDirectMethodMaps.get(context);
        if (current != null) {
          current.putAll(other);
        } else {
          contextualVirtualToDirectMethodMaps.put(context, other);
        }
      }
    }
  }
}
