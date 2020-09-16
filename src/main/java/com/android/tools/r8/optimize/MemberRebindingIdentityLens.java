// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.Invoke.Type;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This lens is used to populate the rebound field and method reference during lookup, such that
 * both the non-rebound and rebound field and method references are available to all descendants of
 * this lens.
 */
public class MemberRebindingIdentityLens extends NonIdentityGraphLens {

  private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;

  private MemberRebindingIdentityLens(
      Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap, GraphLens previousLens) {
    super(previousLens);
    assert !previousLens.hasCodeRewritings();
    this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    assert previous.getReboundReference() == null;
    return FieldLookupResult.builder(this)
        .setReference(previous.getReference())
        .setReboundReference(getReboundFieldReference(previous.getReference()))
        .build();
  }

  private DexField getReboundFieldReference(DexField field) {
    return nonReboundFieldReferenceToDefinitionMap.getOrDefault(field, field);
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return getPrevious().getOriginalType(type);
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    return getPrevious().getOriginalFieldSignature(field);
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    return getPrevious().getOriginalMethodSignature(method);
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField) {
    return getPrevious().getRenamedFieldSignature(originalField);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    return getPrevious().getRenamedMethodSignature(originalMethod, applied);
  }

  @Override
  public DexType lookupType(DexType type) {
    return getPrevious().lookupType(type);
  }

  @Override
  public GraphLensLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
    return getPrevious().lookupMethod(method, context, type);
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    return getPrevious().lookupPrototypeChangesForMethodDefinition(method);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
  }

  public static class Builder {

    private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap =
        new IdentityHashMap<>();

    void recordNonReboundFieldAccesses(FieldAccessInfo fieldAccessInfo) {
      fieldAccessInfo.forEachIndirectAccess(
          nonReboundFieldReference ->
              recordNonReboundFieldAccess(nonReboundFieldReference, fieldAccessInfo.getField()));
    }

    private void recordNonReboundFieldAccess(
        DexField nonReboundFieldReference, DexField reboundFieldReference) {
      nonReboundFieldReferenceToDefinitionMap.put(nonReboundFieldReference, reboundFieldReference);
    }

    MemberRebindingIdentityLens build(GraphLens previousLens) {
      // This intentionally does not return null when the maps are empty. In this case there are no
      // non-rebound field or method references, but the member rebinding lens is still needed to
      // populate the rebound reference during field and method lookup.
      return new MemberRebindingIdentityLens(nonReboundFieldReferenceToDefinitionMap, previousLens);
    }
  }
}
