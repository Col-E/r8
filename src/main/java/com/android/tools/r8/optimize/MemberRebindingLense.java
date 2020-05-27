// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class MemberRebindingLense extends NestedGraphLense {

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

    public GraphLense build(GraphLense previousLense) {
      if (fieldMap.isEmpty() && methodMaps.isEmpty()) {
        return previousLense;
      }
      return new MemberRebindingLense(appView, methodMaps, fieldMap, previousLense);
    }
  }

  private final AppView<?> appView;
  private final Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps;

  public MemberRebindingLense(
      AppView<?> appView,
      Map<Invoke.Type, Map<DexMethod, DexMethod>> methodMaps,
      Map<DexField, DexField> fieldMap,
      GraphLense previousLense) {
    super(
        ImmutableMap.of(),
        ImmutableMap.of(),
        fieldMap,
        null,
        null,
        previousLense,
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
  public GraphLenseLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
    GraphLenseLookupResult previous = previousLense.lookupMethod(method, context, type);
    Map<DexMethod, DexMethod> methodMap = methodMaps.getOrDefault(type, Collections.emptyMap());
    DexMethod newMethod = methodMap.get(previous.getMethod());
    if (newMethod != null) {
      return new GraphLenseLookupResult(
          newMethod, mapInvocationType(newMethod, method, previous.getType()));
    }
    return previous;
  }

  @Override
  protected Type mapInvocationType(DexMethod newMethod, DexMethod originalMethod, Type type) {
    return super.mapVirtualInterfaceInvocationTypes(appView, newMethod, originalMethod, type);
  }
}
