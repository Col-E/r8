// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class MemberRebindingLens extends NestedGraphLens {

  public static class Builder {

    private final AppView<?> appView;

    private final Map<DexField, DexField> fieldMap = new IdentityHashMap<>();
    private final Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps = new IdentityHashMap<>();

    protected Builder(AppView<?> appView) {
      this.appView = appView;
    }

    public void map(DexField from, DexField to) {
      if (from == to) {
        assert !fieldMap.containsKey(from);
        return;
      }
      fieldMap.put(from, to);
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

    public GraphLens build(GraphLens previousLens) {
      if (fieldMap.isEmpty() && methodMaps.isEmpty()) {
        return previousLens;
      }
      return new MemberRebindingLens(appView, methodMaps, fieldMap, previousLens);
    }
  }

  private final AppView<?> appView;
  private final Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps;

  public MemberRebindingLens(
      AppView<?> appView,
      Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps,
      Map<DexField, DexField> fieldMap,
      GraphLens previousLens) {
    super(
        ImmutableMap.of(),
        ImmutableMap.of(),
        fieldMap,
        null,
        null,
        previousLens,
        appView.dexItemFactory());
    this.appView = appView;
    this.methodMaps = methodMaps;
  }

  public static Builder builder(AppView<?> appView) {
    return new Builder(appView);
  }

  @Override
  public boolean isLegitimateToHaveEmptyMappings() {
    return true;
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
        .setType(mapInvocationType(newMethod, previous.getReference(), previous.getType()))
        .build();
  }

  @Override
  protected Type mapInvocationType(DexMethod newMethod, DexMethod originalMethod, Type type) {
    return super.mapVirtualInterfaceInvocationTypes(appView, newMethod, originalMethod, type);
  }
}
