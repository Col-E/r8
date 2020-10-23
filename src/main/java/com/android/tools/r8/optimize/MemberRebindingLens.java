// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import static com.android.tools.r8.graph.GraphLens.NestedGraphLens.mapVirtualInterfaceInvocationTypes;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class MemberRebindingLens extends NonIdentityGraphLens {

  private final AppView<AppInfoWithLiveness> appView;
  private final Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps;
  private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;

  public MemberRebindingLens(
      AppView<AppInfoWithLiveness> appView,
      Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps,
      Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap) {
    super(appView.dexItemFactory(), appView.graphLens());
    this.appView = appView;
    this.methodMaps = methodMaps;
    this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  @Override
  public boolean isMemberRebindingLens() {
    return true;
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
    return this != applied
        ? getPrevious().getRenamedMethodSignature(originalMethod, applied)
        : originalMethod;
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    return getPrevious().lookupPrototypeChangesForMethodDefinition(method);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
  }

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return previous;
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
    Map<DexMethod, DexMethod> methodMap =
        methodMaps.getOrDefault(previous.getType(), Collections.emptyMap());
    DexMethod newMethod = methodMap.get(previous.getReference());
    if (newMethod == null) {
      return previous;
    }
    return MethodLookupResult.builder(this)
        .setReference(newMethod)
        .setPrototypeChanges(previous.getPrototypeChanges())
        .setType(
            mapVirtualInterfaceInvocationTypes(
                appView, newMethod, previous.getReference(), previous.getType()))
        .build();
  }

  @Override
  protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
    return method;
  }

  public FieldRebindingIdentityLens toRewrittenFieldRebindingLens(
      DexItemFactory dexItemFactory, GraphLens lens) {
    FieldRebindingIdentityLens.Builder builder = FieldRebindingIdentityLens.builder();
    nonReboundFieldReferenceToDefinitionMap.forEach(
        (nonReboundFieldReference, reboundFieldReference) -> {
          DexField rewrittenReboundFieldReference = lens.lookupField(reboundFieldReference);
          DexField rewrittenNonReboundFieldReference =
              rewrittenReboundFieldReference.withHolder(
                  lens.lookupType(nonReboundFieldReference.getHolderType()), dexItemFactory);
          builder.recordDefinitionForNonReboundFieldReference(
              rewrittenNonReboundFieldReference, rewrittenReboundFieldReference);
        });
    return builder.build(dexItemFactory);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps = new IdentityHashMap<>();
    private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap =
        new IdentityHashMap<>();

    private Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public void map(DexMethod from, DexMethod to, Invoke.Type type) {
      if (from == to) {
        assert !methodMaps.containsKey(type) || methodMaps.get(type).getOrDefault(from, to) == to;
        return;
      }
      Map<DexMethod, DexMethod> methodMap =
          methodMaps.computeIfAbsent(type, ignore -> new IdentityHashMap<>());
      assert methodMap.getOrDefault(from, to) == to;
      methodMap.put(from, to);
    }

    void recordNonReboundFieldAccesses(FieldAccessInfo info) {
      DexField reboundFieldReference = info.getField();
      info.forEachIndirectAccess(
          nonReboundFieldReference ->
              recordNonReboundFieldAccess(nonReboundFieldReference, reboundFieldReference));
    }

    private void recordNonReboundFieldAccess(
        DexField nonReboundFieldReference, DexField reboundFieldReference) {
      nonReboundFieldReferenceToDefinitionMap.put(nonReboundFieldReference, reboundFieldReference);
    }

    public MemberRebindingLens build() {
      return new MemberRebindingLens(appView, methodMaps, nonReboundFieldReferenceToDefinitionMap);
    }
  }
}
