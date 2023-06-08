// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GraphLens implementation with a parent lens using a simple mapping for type, method and field
 * mapping.
 *
 * <p>Subclasses can override the lookup methods.
 *
 * <p>For method mapping where invocation type can change just override {@link
 * #mapInvocationType(DexMethod, DexMethod, InvokeType)} if the default name mapping applies, and
 * only invocation type might need to change.
 */
public class NestedGraphLens extends DefaultNonIdentityGraphLens {

  protected static final EmptyBidirectionalOneToOneMap<DexField, DexField> EMPTY_FIELD_MAP =
      new EmptyBidirectionalOneToOneMap<>();
  protected static final EmptyBidirectionalOneToOneMap<DexMethod, DexMethod> EMPTY_METHOD_MAP =
      new EmptyBidirectionalOneToOneMap<>();
  protected static final Map<DexType, DexType> EMPTY_TYPE_MAP = Collections.emptyMap();

  protected final BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap;
  protected final Function<DexMethod, DexMethod> methodMap;
  protected final Map<DexType, DexType> typeMap;

  // Map that stores the new signature of methods that have been affected by class merging, unused
  // argument removal, repackaging, synthetic finalization, etc. This is needed to generate a
  // correct mapping file in the end.
  //
  // The difference to `methodMap` is that `methodMap` specifies how invoke-method instructions
  // should be rewritten, whereas this map specifies where methods have been moved. In most cases
  // the two maps are the same, but in a few cases we move a method m1 to m2 and rewrite invokes to
  // m1 to m3.
  //
  // The static type of this map is a many-to-many map to facilitate both one-to-many mappings and
  // many-to-one mappings. Currently, the concrete map is always *either* a one-to-many or a
  // many-to-one map, and not a many-to-many map.
  //
  // One-to-many mappings are generally found when methods are split into two. This could be due to
  // the code object being moved elsewhere (e.g., interface desugaring).
  //
  // Many-to-one mappings are generally found when methods are shared, e.g., due to horizontal class
  // merging of synthetic finalization.
  protected BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> newMethodSignatures;

  // Overrides this if the sub type needs to be a nested lens while it doesn't have any mappings
  // at all, e.g., publicizer lens that changes invocation type only.
  protected boolean isLegitimateToHaveEmptyMappings() {
    return false;
  }

  public NestedGraphLens(AppView<?> appView) {
    this(
        appView,
        NestedGraphLens.EMPTY_FIELD_MAP,
        NestedGraphLens.EMPTY_METHOD_MAP,
        NestedGraphLens.EMPTY_TYPE_MAP);
  }

  public NestedGraphLens(
      AppView<?> appView,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap,
      Map<DexType, DexType> typeMap) {
    this(appView, fieldMap, methodMap.getForwardMap(), typeMap, methodMap);
  }

  public NestedGraphLens(
      AppView<?> appView,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexType, DexType> typeMap,
      BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    this(appView, fieldMap, methodMap::get, typeMap, newMethodSignatures);
    assert !typeMap.isEmpty()
        || !methodMap.isEmpty()
        || !fieldMap.isEmpty()
        || isLegitimateToHaveEmptyMappings();
  }

  public NestedGraphLens(
      AppView<?> appView,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Function<DexMethod, DexMethod> methodMap,
      Map<DexType, DexType> typeMap,
      BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    super(appView);
    this.fieldMap = fieldMap;
    this.methodMap = methodMap;
    this.typeMap = typeMap;
    this.newMethodSignatures = newMethodSignatures;
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
  public DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens) {
    if (this == codeLens) {
      return originalField;
    }
    DexField renamedField = getPrevious().getRenamedFieldSignature(originalField, codeLens);
    return internalGetNextFieldSignature(renamedField);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    if (this == applied) {
      return originalMethod;
    }
    DexMethod renamedMethod = getPrevious().getRenamedMethodSignature(originalMethod, applied);
    return getNextMethodSignature(renamedMethod);
  }

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return typeMap.getOrDefault(previous, previous);
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
                  dexItemFactory());
      return FieldLookupResult.builder(this)
          .setReboundReference(rewrittenReboundReference)
          .setReference(rewrittenNonReboundReference)
          .setReadCastType(previous.getRewrittenReadCastType(this::internalDescribeLookupClassType))
          .setWriteCastType(
              previous.getRewrittenWriteCastType(this::internalDescribeLookupClassType))
          .build();
    } else {
      // TODO(b/168282032): We should always have the rebound reference, so this should become
      //  unreachable.
      DexField rewrittenReference = previous.getRewrittenReference(fieldMap);
      return FieldLookupResult.builder(this)
          .setReference(rewrittenReference)
          .setReadCastType(previous.getRewrittenReadCastType(this::internalDescribeLookupClassType))
          .setWriteCastType(
              previous.getRewrittenWriteCastType(this::internalDescribeLookupClassType))
          .build();
    }
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
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
                  dexItemFactory());
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
      DexMethod newMethod = methodMap.apply(previous.getReference());
      if (newMethod == null) {
        newMethod = previous.getReference();
      }
      RewrittenPrototypeDescription newPrototypeChanges =
          internalDescribePrototypeChanges(previous.getPrototypeChanges(), newMethod);
      if (newMethod == previous.getReference()
          && newPrototypeChanges == previous.getPrototypeChanges()) {
        return previous;
      }
      // TODO(sgjesse): Should we always do interface to virtual mapping? Is it a performance win
      //  that only subclasses which are known to need it actually do it?
      return MethodLookupResult.builder(this)
          .setReference(newMethod)
          .setPrototypeChanges(newPrototypeChanges)
          .setType(mapInvocationType(newMethod, previous.getReference(), previous.getType()))
          .build();
    }
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    if (this == codeLens) {
      return getIdentityLens().lookupPrototypeChangesForMethodDefinition(method, codeLens);
    }
    DexMethod previous = getPreviousMethodSignature(method);
    RewrittenPrototypeDescription lookup =
        getPrevious().lookupPrototypeChangesForMethodDefinition(previous, codeLens);
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
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    return newMethodSignatures.getRepresentativeKeyOrDefault(method, method);
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return newMethodSignatures.getRepresentativeValueOrDefault(method, method);
  }

  /**
   * Default invocation type mapping.
   *
   * <p>This is an identity mapping. If a subclass need invocation type mapping either override this
   * method or {@link #lookupMethod(DexMethod, DexMethod, InvokeType)}
   */
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, InvokeType type) {
    return type;
  }

  /**
   * Standard mapping between interface and virtual invoke type.
   *
   * <p>Handle methods moved from interface to class or class to interface.
   */
  public static InvokeType mapVirtualInterfaceInvocationTypes(
      DexDefinitionSupplier definitions,
      DexMethod newMethod,
      DexMethod originalMethod,
      InvokeType type) {
    if (type == InvokeType.VIRTUAL || type == InvokeType.INTERFACE) {
      // Get the invoke type of the actual definition.
      DexClass newTargetClass = definitions.definitionFor(newMethod.getHolderType());
      if (newTargetClass == null) {
        return type;
      }
      DexClass originalTargetClass = definitions.definitionFor(originalMethod.getHolderType());
      if (originalTargetClass != null
          && (originalTargetClass.isInterface() ^ (type == InvokeType.INTERFACE))) {
        // The invoke was wrong to start with, so we keep it wrong. This is to ensure we get
        // the IncompatibleClassChangeError the original invoke would have triggered.
        return newTargetClass.accessFlags.isInterface() ? InvokeType.VIRTUAL : InvokeType.INTERFACE;
      }
      return newTargetClass.accessFlags.isInterface() ? InvokeType.INTERFACE : InvokeType.VIRTUAL;
    }
    return type;
  }

  @Override
  public boolean verifyIsContextFreeForMethod(DexMethod method, GraphLens codeLens) {
    assert codeLens == this
        || getPrevious().verifyIsContextFreeForMethod(getPreviousMethodSignature(method), codeLens);
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    typeMap.forEach(
        (from, to) ->
            builder
                .append(from.getTypeName())
                .append(" -> ")
                .append(to.getTypeName())
                .append(System.lineSeparator()));
    fieldMap.forEachManyToOneMapping(
        (fromSet, to) -> {
          builder.append(
              fromSet.stream()
                  .map(DexField::toSourceString)
                  .collect(Collectors.joining("," + System.lineSeparator())));
          builder.append(" -> ");
          builder.append(to.toSourceString()).append(System.lineSeparator());
        });
    builder.append(getPrevious().toString());
    return builder.toString();
  }
}
