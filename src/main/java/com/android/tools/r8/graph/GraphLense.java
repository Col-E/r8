// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A GraphLense implements a virtual view on top of the graph, used to delay global rewrites until
 * later IR processing stages.
 * <p>
 * Valid remappings are limited to the following operations:
 * <ul>
 * <li>Mapping a classes type to one of the super/subtypes.</li>
 * <li>Renaming private methods/fields.</li>
 * <li>Moving methods/fields to a super/subclass.</li>
 * <li>Replacing method/field references by the same method/field on a super/subtype</li>
 * </ul>
 * Note that the latter two have to take visibility into account.
 */
public abstract class GraphLense {

  public static class Builder {

    protected Builder() {
    }

    protected final Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    protected final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();
    protected final Map<DexField, DexField> fieldMap = new IdentityHashMap<>();

    public void map(DexType from, DexType to) {
      typeMap.put(from, to);
    }

    public void map(DexMethod from, DexMethod to) {
      methodMap.put(from, to);
    }

    public void map(DexField from, DexField to) {
      fieldMap.put(from, to);
    }

    public GraphLense build(DexItemFactory dexItemFactory) {
      return build(dexItemFactory, new IdentityGraphLense());
    }

    public GraphLense build(DexItemFactory dexItemFactory, GraphLense previousLense) {
      if (typeMap.isEmpty() && methodMap.isEmpty() && fieldMap.isEmpty()) {
        return previousLense;
      }
      return new NestedGraphLense(typeMap, methodMap, fieldMap, previousLense, dexItemFactory);
    }

  }

  public static Builder builder() {
    return new Builder();
  }

  public abstract DexType lookupType(DexType type);

  // This overload can be used when the graph lense is known to be context insensitive.
  public DexMethod lookupMethod(DexMethod method) {
    assert isContextFreeForMethod(method);
    return lookupMethod(method, null, null);
  }

  public abstract DexMethod lookupMethod(DexMethod method, DexEncodedMethod context, Type type);

  // Context sensitive graph lenses should override this method.
  public Set<DexMethod> lookupMethodInAllContexts(DexMethod method) {
    assert isContextFreeForMethod(method);
    DexMethod result = lookupMethod(method);
    if (result != null) {
      return ImmutableSet.of(result);
    }
    return ImmutableSet.of();
  }

  public abstract DexField lookupField(DexField field);

  public abstract boolean isContextFreeForMethods();

  public boolean isContextFreeForMethod(DexMethod method) {
    return isContextFreeForMethods();
  }

  public static GraphLense getIdentityLense() {
    return new IdentityGraphLense();
  }

  public final boolean isIdentityLense() {
    return this instanceof IdentityGraphLense;
  }

  public boolean assertNotModified(Iterable<DexItem> items) {
    for (DexItem item : items) {
      if (item instanceof DexClass) {
        DexType type = ((DexClass) item).type;
        assert lookupType(type) == type;
      } else if (item instanceof DexEncodedMethod) {
        DexEncodedMethod method = (DexEncodedMethod) item;
        // We allow changes to bridge methods as these get retargeted even if they are kept.
        assert method.accessFlags.isBridge() || lookupMethod(method.method) == method.method;
      } else if (item instanceof DexEncodedField) {
        DexField field = ((DexEncodedField) item).field;
        assert lookupField(field) == field;
      } else {
        assert false;
      }
    }
    return true;
  }

  private static class IdentityGraphLense extends GraphLense {

    @Override
    public DexType lookupType(DexType type) {
      return type;
    }

    @Override
    public DexMethod lookupMethod(DexMethod method, DexEncodedMethod context, Type type) {
      return method;
    }

    @Override
    public DexField lookupField(DexField field) {
      return field;
    }

    @Override
    public boolean isContextFreeForMethods() {
      return true;
    }
  }

  public static class NestedGraphLense extends GraphLense {

    private final GraphLense previousLense;
    protected final DexItemFactory dexItemFactory;

    private final Map<DexType, DexType> typeMap;
    private final Map<DexType, DexType> arrayTypeCache = new IdentityHashMap<>();
    private final Map<DexMethod, DexMethod> methodMap;
    private final Map<DexField, DexField> fieldMap;

    public NestedGraphLense(Map<DexType, DexType> typeMap, Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap, GraphLense previousLense, DexItemFactory dexItemFactory) {
      this.typeMap = typeMap;
      this.methodMap = methodMap;
      this.fieldMap = fieldMap;
      this.previousLense = previousLense;
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public DexType lookupType(DexType type) {
      if (type.isArrayType()) {
        synchronized (this) {
          // This block need to be synchronized due to arrayTypeCache.
          DexType result = arrayTypeCache.get(type);
          if (result == null) {
            DexType baseType = type.toBaseType(dexItemFactory);
            DexType newType = lookupType(baseType);
            if (baseType == newType) {
              result = type;
            } else {
              result = type.replaceBaseType(newType, dexItemFactory);
            }
            arrayTypeCache.put(type, result);
          }
          return result;
        }
      }
      DexType previous = previousLense.lookupType(type);
      return typeMap.getOrDefault(previous, previous);
    }

    @Override
    public DexMethod lookupMethod(DexMethod method, DexEncodedMethod context, Type type) {
      DexMethod previous = previousLense.lookupMethod(method, context, type);
      return methodMap.getOrDefault(previous, previous);
    }

    @Override
    public Set<DexMethod> lookupMethodInAllContexts(DexMethod method) {
      Set<DexMethod> result = new HashSet<>();
      for (DexMethod previous : previousLense.lookupMethodInAllContexts(method)) {
        result.add(methodMap.getOrDefault(previous, previous));
      }
      return result;
    }

    @Override
    public DexField lookupField(DexField field) {
      DexField previous = previousLense.lookupField(field);
      return fieldMap.getOrDefault(previous, previous);
    }

    @Override
    public boolean isContextFreeForMethods() {
      return previousLense.isContextFreeForMethods();
    }

    @Override
    public boolean isContextFreeForMethod(DexMethod method) {
      return previousLense.isContextFreeForMethod(method);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      for (Map.Entry<DexType, DexType> entry : typeMap.entrySet()) {
        builder.append(entry.getKey().toSourceString()).append(" -> ");
        builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
      }
      for (Map.Entry<DexMethod, DexMethod> entry : methodMap.entrySet()) {
        builder.append(entry.getKey().toSourceString()).append(" -> ");
        builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
      }
      for (Map.Entry<DexField, DexField> entry : fieldMap.entrySet()) {
        builder.append(entry.getKey().toSourceString()).append(" -> ");
        builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
      }
      builder.append(previousLense.toString());
      return builder.toString();
    }
  }
}
