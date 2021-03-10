// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GraphLens implementation with a parent lens using a simple mapping for type, method and field
 * mapping.
 *
 * <p>Subclasses can override the lookup methods.
 *
 * <p>For method mapping where invocation type can change just override {@link
 * #mapInvocationType(DexMethod, DexMethod, Type)} if the default name mapping applies, and only
 * invocation type might need to change.
 */
public class LegacyNestedGraphLens extends NonIdentityGraphLens {

  protected final DexItemFactory dexItemFactory;

  protected final Map<DexType, DexType> typeMap;
  protected final Map<DexMethod, DexMethod> methodMap;
  protected final BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap;

  // Map that store the original signature of methods that have been affected, for example, by
  // vertical class merging. Needed to generate a correct Proguard map in the end.
  protected BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> originalMethodSignatures;

  // Overrides this if the sub type needs to be a nested lens while it doesn't have any mappings
  // at all, e.g., publicizer lens that changes invocation type only.
  protected boolean isLegitimateToHaveEmptyMappings() {
    return false;
  }

  public LegacyNestedGraphLens(
      Map<DexType, DexType> typeMap,
      Map<DexMethod, DexMethod> methodMap,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> originalMethodSignatures,
      GraphLens previousLens,
      DexItemFactory dexItemFactory) {
    super(dexItemFactory, previousLens);
    assert !typeMap.isEmpty()
        || !methodMap.isEmpty()
        || !fieldMap.isEmpty()
        || isLegitimateToHaveEmptyMappings();
    this.typeMap = typeMap.isEmpty() ? null : typeMap;
    this.methodMap = methodMap;
    this.fieldMap = fieldMap;
    this.originalMethodSignatures = originalMethodSignatures;
    this.dexItemFactory = dexItemFactory;
  }

  public static Builder builder() {
    return new Builder();
  }

  protected DexType internalGetOriginalType(DexType previous) {
    return previous;
  }

  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    return IterableUtils.singleton(internalGetOriginalType(previous));
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return getPrevious().getOriginalType(internalGetOriginalType(type));
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    return IterableUtils.flatMap(internalGetOriginalTypes(type), getPrevious()::getOriginalTypes);
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    DexField originalField = fieldMap.getRepresentativeKeyOrDefault(field, field);
    return getPrevious().getOriginalFieldSignature(originalField);
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    DexMethod originalMethod = internalGetPreviousMethodSignature(method);
    return getPrevious().getOriginalMethodSignature(originalMethod);
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField) {
    DexField renamedField = getPrevious().getRenamedFieldSignature(originalField);
    return internalGetNextFieldSignature(renamedField);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    if (this == applied) {
      return originalMethod;
    }
    DexMethod renamedMethod = getPrevious().getRenamedMethodSignature(originalMethod, applied);
    return internalGetNextMethodSignature(renamedMethod);
  }

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return typeMap != null ? typeMap.getOrDefault(previous, previous) : previous;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    if (previous.hasReboundReference()) {
      // Rewrite the rebound reference and then "fixup" the non-rebound reference.
      DexField rewrittenReboundReference = previous.getRewrittenReboundReference(fieldMap);
      DexField rewrittenNonReboundReference =
          previous.getReference() == previous.getReboundReference()
              ? rewrittenReboundReference
              : rewrittenReboundReference.withHolder(
                  internalDescribeLookupClassType(previous.getReference().getHolderType()),
                  dexItemFactory);
      return FieldLookupResult.builder(this)
          .setReboundReference(rewrittenReboundReference)
          .setReference(rewrittenNonReboundReference)
          .setCastType(previous.getRewrittenCastType(this::internalDescribeLookupClassType))
          .build();
    } else {
      // TODO(b/168282032): We should always have the rebound reference, so this should become
      //  unreachable.
      DexField rewrittenReference = previous.getRewrittenReference(fieldMap);
      return FieldLookupResult.builder(this)
          .setReference(rewrittenReference)
          .setCastType(previous.getRewrittenCastType(this::internalDescribeLookupClassType))
          .build();
    }
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    if (previous.hasReboundReference()) {
      // TODO(sgjesse): Should we always do interface to virtual mapping? Is it a performance win
      //  that only subclasses which are known to need it actually do it?
      DexMethod rewrittenReboundReference = previous.getRewrittenReboundReference(methodMap);
      DexMethod rewrittenReference =
          previous.getReference() == previous.getReboundReference()
              ? rewrittenReboundReference
              : // This assumes that the holder will always be moved in lock-step with the method!
              rewrittenReboundReference.withHolder(
                  internalDescribeLookupClassType(previous.getReference().getHolderType()),
                  dexItemFactory);
      return MethodLookupResult.builder(this)
          .setReference(rewrittenReference)
          .setReboundReference(rewrittenReboundReference)
          .setPrototypeChanges(
              internalDescribePrototypeChanges(
                  previous.getPrototypeChanges(), rewrittenReboundReference))
          .setType(
              mapInvocationType(
                  rewrittenReboundReference, previous.getReference(), previous.getType()))
          .build();
    } else {
      // TODO(b/168282032): We should always have the rebound reference, so this should become
      //  unreachable.
      DexMethod newMethod = methodMap.get(previous.getReference());
      if (newMethod == null) {
        return previous;
      }
      // TODO(sgjesse): Should we always do interface to virtual mapping? Is it a performance win
      //  that only subclasses which are known to need it actually do it?
      return MethodLookupResult.builder(this)
          .setReference(newMethod)
          .setPrototypeChanges(
              internalDescribePrototypeChanges(previous.getPrototypeChanges(), newMethod))
          .setType(mapInvocationType(newMethod, previous.getReference(), previous.getType()))
          .build();
    }
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    DexMethod previous = internalGetPreviousMethodSignature(method);
    RewrittenPrototypeDescription lookup =
        getPrevious().lookupPrototypeChangesForMethodDefinition(previous);
    return internalDescribePrototypeChanges(lookup, method);
  }

  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    return prototypeChanges;
  }

  protected DexField internalGetNextFieldSignature(DexField field) {
    return fieldMap.getOrDefault(field, field);
  }

  @Override
  protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    return originalMethodSignatures.getRepresentativeValueOrDefault(method, method);
  }

  protected DexMethod internalGetNextMethodSignature(DexMethod method) {
    return originalMethodSignatures.getRepresentativeKeyOrDefault(method, method);
  }

  @Override
  public DexMethod lookupGetFieldForMethod(DexField field, DexMethod context) {
    return getPrevious().lookupGetFieldForMethod(field, context);
  }

  @Override
  public DexMethod lookupPutFieldForMethod(DexField field, DexMethod context) {
    return getPrevious().lookupPutFieldForMethod(field, context);
  }

  /**
   * Default invocation type mapping.
   *
   * <p>This is an identity mapping. If a subclass need invocation type mapping either override this
   * method or {@link #lookupMethod(DexMethod, DexMethod, Type)}
   */
  protected Type mapInvocationType(DexMethod newMethod, DexMethod originalMethod, Type type) {
    return type;
  }

  /**
   * Standard mapping between interface and virtual invoke type.
   *
   * <p>Handle methods moved from interface to class or class to interface.
   */
  public static Type mapVirtualInterfaceInvocationTypes(
      DexDefinitionSupplier definitions, DexMethod newMethod, DexMethod originalMethod, Type type) {
    if (type == Type.VIRTUAL || type == Type.INTERFACE) {
      // Get the invoke type of the actual definition.
      DexClass newTargetClass = definitions.definitionFor(newMethod.getHolderType());
      if (newTargetClass == null) {
        return type;
      }
      DexClass originalTargetClass = definitions.definitionFor(originalMethod.getHolderType());
      if (originalTargetClass != null
          && (originalTargetClass.isInterface() ^ (type == Type.INTERFACE))) {
        // The invoke was wrong to start with, so we keep it wrong. This is to ensure we get
        // the IncompatibleClassChangeError the original invoke would have triggered.
        return newTargetClass.accessFlags.isInterface() ? Type.VIRTUAL : Type.INTERFACE;
      }
      return newTargetClass.accessFlags.isInterface() ? Type.INTERFACE : Type.VIRTUAL;
    }
    return type;
  }

  @Override
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
  }

  @Override
  public boolean verifyIsContextFreeForMethod(DexMethod method) {
    assert getPrevious().verifyIsContextFreeForMethod(method);
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (typeMap != null) {
      for (Map.Entry<DexType, DexType> entry : typeMap.entrySet()) {
        builder.append(entry.getKey().toSourceString()).append(" -> ");
        builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
      }
    }
    for (Map.Entry<DexMethod, DexMethod> entry : methodMap.entrySet()) {
      builder.append(entry.getKey().toSourceString()).append(" -> ");
      builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
    }
    fieldMap.forEachManyToOneMapping(
        (keys, value) -> {
          builder.append(
              keys.stream()
                  .map(DexField::toSourceString)
                  .collect(Collectors.joining("," + System.lineSeparator())));
          builder.append(" -> ");
          builder.append(value.toSourceString()).append(System.lineSeparator());
        });
    builder.append(getPrevious().toString());
    return builder.toString();
  }
}
