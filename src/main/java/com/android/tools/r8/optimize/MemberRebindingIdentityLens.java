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
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This lens is used to populate the rebound field and method reference during lookup, such that
 * both the non-rebound and rebound field and method references are available to all descendants of
 * this lens.
 */
public class MemberRebindingIdentityLens extends DefaultNonIdentityGraphLens {

  private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;
  private final Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap;

  private MemberRebindingIdentityLens(
      Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap,
      Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap,
      DexItemFactory dexItemFactory,
      GraphLens previousLens) {
    super(dexItemFactory, previousLens);
    this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
    this.nonReboundMethodReferenceToDefinitionMap = nonReboundMethodReferenceToDefinitionMap;
  }

  public static Builder builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return builder(appView, appView.graphLens());
  }

  public static Builder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, GraphLens previousLens) {
    return new Builder(appView, previousLens);
  }

  public void addNonReboundMethodReference(
      DexMethod nonReboundMethodReference, DexMethod reboundMethodReference) {
    nonReboundMethodReferenceToDefinitionMap.put(nonReboundMethodReference, reboundMethodReference);
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    assert !previous.hasReadCastType();
    assert !previous.hasReboundReference();
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
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert previous.getReboundReference() == null;
    return MethodLookupResult.builder(this)
        .setReference(previous.getReference())
        .setReboundReference(getReboundMethodReference(previous.getReference()))
        .setPrototypeChanges(previous.getPrototypeChanges())
        .setType(previous.getType())
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  private DexMethod getReboundMethodReference(DexMethod method) {
    DexMethod rebound = nonReboundMethodReferenceToDefinitionMap.get(method);
    assert method != rebound;
    while (rebound != null) {
      method = rebound;
      rebound = nonReboundMethodReferenceToDefinitionMap.get(method);
    }
    return method;
  }

  @Override
  public boolean isMemberRebindingIdentityLens() {
    return true;
  }

  @Override
  public MemberRebindingIdentityLens asMemberRebindingIdentityLens() {
    return this;
  }

  public MemberRebindingIdentityLens toRewrittenMemberRebindingIdentityLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens lens,
      NonIdentityGraphLens appliedMemberRebindingLens) {
    return toRewrittenMemberRebindingIdentityLens(
        appView, lens, appliedMemberRebindingLens, getIdentityLens());
  }

  public MemberRebindingIdentityLens toRewrittenMemberRebindingIdentityLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens lens,
      NonIdentityGraphLens appliedMemberRebindingLens,
      GraphLens newPreviousLens) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Builder builder = builder(appView, newPreviousLens);
    nonReboundFieldReferenceToDefinitionMap.forEach(
        (nonReboundFieldReference, reboundFieldReference) -> {
          DexField rewrittenReboundFieldReference =
              lens.lookupField(reboundFieldReference, appliedMemberRebindingLens);
          DexField rewrittenNonReboundFieldReference =
              rewrittenReboundFieldReference.withHolder(
                  lens.lookupType(
                      nonReboundFieldReference.getHolderType(), appliedMemberRebindingLens),
                  dexItemFactory);
          builder.recordNonReboundFieldAccess(
              rewrittenNonReboundFieldReference, rewrittenReboundFieldReference);
        });
    nonReboundMethodReferenceToDefinitionMap.forEach(
        (nonReboundMethodReference, reboundMethodReference) -> {
          DexMethod rewrittenReboundMethodReference =
              lens.getRenamedMethodSignature(reboundMethodReference, appliedMemberRebindingLens);
          DexMethod rewrittenNonReboundMethodReference =
              rewrittenReboundMethodReference.withHolder(
                  lens.lookupType(
                      nonReboundMethodReference.getHolderType(), appliedMemberRebindingLens),
                  dexItemFactory);
          builder.recordNonReboundMethodAccess(
              rewrittenNonReboundMethodReference, rewrittenReboundMethodReference);
        });
    return builder.build();
  }

  public static class Builder {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final GraphLens previousLens;

    private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap =
        new IdentityHashMap<>();
    private final Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap =
        new IdentityHashMap<>();

    private Builder(AppView<? extends AppInfoWithClassHierarchy> appView, GraphLens previousLens) {
      this.appView = appView;
      this.previousLens = previousLens;
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

    private void recordNonReboundMethodAccess(
        DexMethod nonReboundMethodReference, DexMethod reboundMethodReference) {
      nonReboundMethodReferenceToDefinitionMap.put(
          nonReboundMethodReference, reboundMethodReference);
    }

    void recordMethodAccess(DexMethod reference) {
      if (reference.getHolderType().isArrayType()) {
        return;
      }
      DexClass holder = appView.contextIndependentDefinitionFor(reference.getHolderType());
      if (holder != null) {
        SingleResolutionResult<?> resolutionResult =
            appView.appInfo().resolveMethodOnLegacy(holder, reference).asSingleResolution();
        if (resolutionResult != null && resolutionResult.getResolvedHolder() != holder) {
          recordNonReboundMethodAccess(
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
          previousLens);
    }
  }
}
