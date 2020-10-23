// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This lens is used to populate the rebound field and method reference during lookup, such that
 * both the non-rebound and rebound field and method references are available to all descendants of
 * this lens.
 */
public class MemberRebindingIdentityLens extends NonIdentityGraphLens {

  private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;
  private final Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap;

  private MemberRebindingIdentityLens(
      Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap,
      Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap,
      DexItemFactory dexItemFactory,
      GraphLens previousLens) {
    super(dexItemFactory, previousLens);
    assert !previousLens.hasCodeRewritings();
    this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
    this.nonReboundMethodReferenceToDefinitionMap = nonReboundMethodReferenceToDefinitionMap;
  }

  public static Builder builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new Builder(appView);
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
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
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    assert previous.getReboundReference() == null;
    return MethodLookupResult.builder(this)
        .setReference(previous.getReference())
        .setReboundReference(getReboundMethodReference(previous.getReference()))
        .setPrototypeChanges(previous.getPrototypeChanges())
        .setType(previous.getType())
        .build();
  }

  private DexMethod getReboundMethodReference(DexMethod method) {
    return nonReboundMethodReferenceToDefinitionMap.getOrDefault(method, method);
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
  public final DexType internalDescribeLookupClassType(DexType previous) {
    return previous;
  }

  @Override
  protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    return method;
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

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap =
        new IdentityHashMap<>();
    private final Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap =
        new IdentityHashMap<>();

    private Builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
      this.appView = appView;
    }

    void recordNonReboundFieldAccesses(FieldAccessInfo fieldAccessInfo) {
      fieldAccessInfo.forEachIndirectAccess(
          nonReboundFieldReference ->
              recordNonReboundFieldAccess(nonReboundFieldReference, fieldAccessInfo.getField()));
    }

    private void recordNonReboundFieldAccess(
        DexField nonReboundFieldReference, DexField reboundFieldReference) {
      nonReboundFieldReferenceToDefinitionMap.put(nonReboundFieldReference, reboundFieldReference);
    }

    void recordMethodAccess(DexMethod reference) {
      if (reference.getHolderType().isArrayType()) {
        return;
      }
      DexClass holder = appView.contextIndependentDefinitionFor(reference.getHolderType());
      if (holder != null) {
        SingleResolutionResult resolutionResult =
            appView.appInfo().resolveMethodOn(holder, reference).asSingleResolution();
        if (resolutionResult != null && resolutionResult.getResolvedHolder() != holder) {
          nonReboundMethodReferenceToDefinitionMap.put(
              reference, resolutionResult.getResolvedMethod().getReference());
        }
      }
    }

    MemberRebindingIdentityLens build() {
      // This intentionally does not return null when the maps are empty. In this case there are no
      // non-rebound field or method references, but the member rebinding lens is still needed to
      // populate the rebound reference during field and method lookup.
      return new MemberRebindingIdentityLens(
          nonReboundFieldReferenceToDefinitionMap,
          nonReboundMethodReferenceToDefinitionMap,
          appView.dexItemFactory(),
          appView.graphLens());
    }
  }
}
