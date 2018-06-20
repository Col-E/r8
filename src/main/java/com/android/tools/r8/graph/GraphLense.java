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
 * <li>Moved methods might require changed invocation type at the call site</li>
 * </ul>
 * Note that the latter two have to take visibility into account.
 */
public abstract class GraphLense {

  /**
   * Result of a method lookup in a GraphLense.
   *
   * This provide the new target and the invoke type to use.
   */
  public static class GraphLenseLookupResult {
    private final DexMethod method;
    private final Type type;

    public GraphLenseLookupResult(DexMethod method, Type type) {
      this.method = method;
      this.type = type;
    }

    public DexMethod getMethod() {
      return method;
    }

    public Type getType() {
      return type;
    }
  }

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
    return lookupMethod(method, null, null).getMethod();
  }

  public abstract GraphLenseLookupResult lookupMethod(
      DexMethod method, DexEncodedMethod context, Type type);

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

  private static class IdentityGraphLense extends GraphLense {

    @Override
    public DexType lookupType(DexType type) {
      return type;
    }

    @Override
    public GraphLenseLookupResult lookupMethod(
        DexMethod method, DexEncodedMethod context, Type type) {
      return new GraphLenseLookupResult(method, type);
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

  /**
   * GraphLense implementation with a parent lense using a simple mapping for type, method and
   * field mapping.
   *
   * Subclasses can override the lookup methods.
   *
   * For method mapping where invocation type can change just override
   * {@link #mapInvocationType(DexMethod, DexMethod, DexEncodedMethod, Type)} if
   * the default name mapping applies, and only invocation type might need to change.
   */
  public static class NestedGraphLense extends GraphLense {

    protected final GraphLense previousLense;
    protected final DexItemFactory dexItemFactory;

    protected final Map<DexType, DexType> typeMap;
    private final Map<DexType, DexType> arrayTypeCache = new IdentityHashMap<>();
    protected final Map<DexMethod, DexMethod> methodMap;
    protected final Map<DexField, DexField> fieldMap;

    public NestedGraphLense(Map<DexType, DexType> typeMap, Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap, GraphLense previousLense, DexItemFactory dexItemFactory) {
      this.typeMap = typeMap.isEmpty() ? null : typeMap;
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
      return typeMap != null ? typeMap.getOrDefault(previous, previous) : previous;
    }

    @Override
    public GraphLenseLookupResult lookupMethod(
        DexMethod method, DexEncodedMethod context, Type type) {
      GraphLenseLookupResult previous = previousLense.lookupMethod(method, context, type);
      DexMethod newMethod = methodMap.get(previous.getMethod());
      if (newMethod == null) {
        return previous;
      }
      // TODO(sgjesse): Should we always do interface to virtual mapping? Is it a performance win
      // that only subclasses which are known to need it actually do it?
      return new GraphLenseLookupResult(
          newMethod, mapInvocationType(newMethod, method, context, type));
    }

    /**
     * Default invocation type mapping.
     *
     * This is an identity mapping. If a subclass need invocation type mapping either override
     * this method or {@link #lookupMethod(DexMethod, DexEncodedMethod, Type)}
     */
    protected Type mapInvocationType(
        DexMethod newMethod, DexMethod originalMethod, DexEncodedMethod context, Type type) {
      return type;
    }

    /**
     * Standard mapping between interface and virtual invoke type.
     *
     * Handle methods moved from interface to class or class to interface.
     */
    final protected Type mapVirtualInterfaceInvocationTypes(
        AppInfo appInfo, DexMethod newMethod, DexMethod originalMethod,
        DexEncodedMethod context, Type type) {
      if (type == Type.VIRTUAL || type == Type.INTERFACE) {
        // Get the invoke type of the actual definition.
        DexClass newTargetClass = appInfo.definitionFor(newMethod.holder);
        if (newTargetClass == null) {
          return type;
        }
        DexClass originalTargetClass = appInfo.definitionFor(originalMethod.holder);
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
