// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// This graph lense is instantiated during vertical class merging. The graph lense is context
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
// For the invocation "invoke-super A.m()" in B.m, this graph lense will return the newly created,
// private method corresponding to A.m (that is now in B.m with a fresh name), such that the
// invocation will hit the same implementation as the original super.m() call.
//
// For the invocation "invoke-virtual A.m()" in B.m2, this graph lense will return the method B.m.
public class VerticalClassMergerGraphLense extends GraphLense {
  private final GraphLense previousLense;

  private final Map<DexField, DexField> fieldMap;
  private final Map<DexMethod, DexMethod> methodMap;
  private final Set<DexMethod> mergedMethods;
  private final Map<DexType, Map<DexMethod, DexMethod>> contextualVirtualToDirectMethodMaps;

  public VerticalClassMergerGraphLense(
      Map<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      Set<DexMethod> mergedMethods,
      Map<DexType, Map<DexMethod, DexMethod>> contextualVirtualToDirectMethodMaps,
      GraphLense previousLense) {
    this.previousLense = previousLense;
    this.fieldMap = fieldMap;
    this.methodMap = methodMap;
    this.mergedMethods = mergedMethods;
    this.contextualVirtualToDirectMethodMaps = contextualVirtualToDirectMethodMaps;
  }

  @Override
  public DexType lookupType(DexType type) {
    return previousLense.lookupType(type);
  }

  @Override
  public DexMethod lookupMethod(DexMethod method, DexEncodedMethod context, Type type) {
    assert isContextFreeForMethod(method) || (context != null && type != null);
    DexMethod previous = previousLense.lookupMethod(method, context, type);
    if (type == Type.SUPER && !mergedMethods.contains(context.method)) {
      Map<DexMethod, DexMethod> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.get(context.method.holder);
      if (virtualToDirectMethodMap != null) {
        DexMethod directMethod = virtualToDirectMethodMap.get(previous);
        if (directMethod != null) {
          return directMethod;
        }
      }
    }
    return methodMap.getOrDefault(previous, previous);
  }

  @Override
  public Set<DexMethod> lookupMethodInAllContexts(DexMethod method) {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    for (DexMethod previous : previousLense.lookupMethodInAllContexts(method)) {
      builder.add(methodMap.getOrDefault(previous, previous));
      for (Map<DexMethod, DexMethod> virtualToDirectMethodMap :
          contextualVirtualToDirectMethodMaps.values()) {
        DexMethod directMethod = virtualToDirectMethodMap.get(previous);
        if (directMethod != null) {
          builder.add(directMethod);
        }
      }
    }
    return builder.build();
  }

  @Override
  public DexField lookupField(DexField field) {
    DexField previous = previousLense.lookupField(field);
    return fieldMap.getOrDefault(previous, previous);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return contextualVirtualToDirectMethodMaps.isEmpty() && previousLense.isContextFreeForMethods();
  }

  @Override
  public boolean isContextFreeForMethod(DexMethod method) {
    if (!previousLense.isContextFreeForMethod(method)) {
      return false;
    }
    DexMethod previous = previousLense.lookupMethod(method);
    for (Map<DexMethod, DexMethod> virtualToDirectMethodMap :
        contextualVirtualToDirectMethodMaps.values()) {
      if (virtualToDirectMethodMap.containsKey(previous)) {
        return false;
      }
    }
    return true;
  }

  public static class Builder {

    private final ImmutableMap.Builder<DexField, DexField> fieldMapBuilder = ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod> methodMapBuilder =
        ImmutableMap.builder();
    private final ImmutableSet.Builder<DexMethod> mergedMethodsBuilder = ImmutableSet.builder();
    private final Map<DexType, Map<DexMethod, DexMethod>> contextualVirtualToDirectMethodMaps =
        new HashMap<>();

    public Builder() {}

    public GraphLense build(GraphLense previousLense) {
      Map<DexField, DexField> fieldMap = fieldMapBuilder.build();
      Map<DexMethod, DexMethod> methodMap = methodMapBuilder.build();
      if (fieldMap.isEmpty()
          && methodMap.isEmpty()
          && contextualVirtualToDirectMethodMaps.isEmpty()) {
        return previousLense;
      }
      return new VerticalClassMergerGraphLense(
          fieldMap,
          methodMap,
          mergedMethodsBuilder.build(),
          contextualVirtualToDirectMethodMaps,
          previousLense);
    }

    public void markMethodAsMerged(DexMethod method) {
      mergedMethodsBuilder.add(method);
    }

    public void map(DexField from, DexField to) {
      fieldMapBuilder.put(from, to);
    }

    public void map(DexMethod from, DexMethod to) {
      methodMapBuilder.put(from, to);
    }

    public void mapVirtualMethodToDirectInType(DexMethod from, DexMethod to, DexType type) {
      Map<DexMethod, DexMethod> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.computeIfAbsent(type, key -> new HashMap<>());
      virtualToDirectMethodMap.put(from, to);
    }

    public void merge(VerticalClassMergerGraphLense.Builder builder) {
      fieldMapBuilder.putAll(builder.fieldMapBuilder.build());
      methodMapBuilder.putAll(builder.methodMapBuilder.build());
      mergedMethodsBuilder.addAll(builder.mergedMethodsBuilder.build());
      for (DexType context : builder.contextualVirtualToDirectMethodMaps.keySet()) {
        Map<DexMethod, DexMethod> current = contextualVirtualToDirectMethodMaps.get(context);
        Map<DexMethod, DexMethod> other = builder.contextualVirtualToDirectMethodMaps.get(context);
        if (current != null) {
          current.putAll(other);
        } else {
          contextualVirtualToDirectMethodMaps.put(context, other);
        }
      }
    }
  }
}
