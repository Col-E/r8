// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
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
      return new NestedGraphLense(
          typeMap, methodMap, fieldMap, null, null, previousLense, dexItemFactory);
    }

  }

  public static Builder builder() {
    return new Builder();
  }

  public abstract DexField getOriginalFieldSignature(DexField field);

  public abstract DexMethod getOriginalMethodSignature(DexMethod method);

  public abstract DexField getRenamedFieldSignature(DexField originalField);

  public abstract DexMethod getRenamedMethodSignature(DexMethod originalMethod);

  public DexEncodedMethod mapDexEncodedMethod(
      AppInfo appInfo, DexEncodedMethod originalEncodedMethod) {
    DexMethod newMethod = getRenamedMethodSignature(originalEncodedMethod.method);
    if (newMethod != originalEncodedMethod.method) {
      // We can't directly use AppInfo#definitionFor(DexMethod) since definitions may not be
      // updated either yet.
      DexClass newHolder = appInfo.definitionFor(newMethod.holder);
      assert newHolder != null;
      DexEncodedMethod newEncodedMethod = newHolder.lookupMethod(newMethod);
      assert newEncodedMethod != null;
      return newEncodedMethod;
    }
    return originalEncodedMethod;
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

  // The method lookupMethod() maps a pair INVOKE=(method signature, invoke type) to a new pair
  // INVOKE'=(method signature', invoke type'). This mapping can be context sensitive, meaning that
  // the result INVOKE' depends on where the invocation INVOKE is in the program. This is, for
  // example, used by the vertical class merger to translate invoke-super instructions that hit
  // a method in the direct super class to invoke-direct instructions after class merging.
  //
  // This method can be used to determine if a graph lense is context sensitive. If a graph lense
  // is context insensitive, it is safe to invoke lookupMethod() without a context (or to pass null
  // as context). Trying to invoke a context sensitive graph lense without a context will lead to
  // an assertion error.
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

  public <T extends DexItem> boolean assertNotModified(Iterable<T> items) {
    for (DexItem item : items) {
      // TODO(b/67934123) There should be a common interface to perform the dispatch.
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

  public ImmutableSet<DexItem> rewriteMixedItemsConservatively(Set<DexItem> original) {
    ImmutableSet.Builder<DexItem> builder = ImmutableSet.builder();
    for (DexItem item : original) {
      // TODO(b/67934123) There should be a common interface to perform the dispatch.
      if (item instanceof DexType) {
        builder.add(lookupType((DexType) item));
      } else if (item instanceof DexMethod) {
        DexMethod method = (DexMethod) item;
        if (isContextFreeForMethod(method)) {
          builder.add(lookupMethod(method));
        } else {
          builder.addAll(lookupMethodInAllContexts(method));
        }
      } else if (item instanceof DexField) {
        builder.add(lookupField((DexField) item));
      } else {
        throw new Unreachable();
      }
    }
    return builder.build();
  }

  public Object2BooleanMap<DexItem> rewriteMixedItemsConservatively(
      Object2BooleanMap<DexItem> original) {
    Object2BooleanMap<DexItem> result = new Object2BooleanArrayMap<>();
    for (Object2BooleanMap.Entry<DexItem> entry : original.object2BooleanEntrySet()) {
      DexItem item = entry.getKey();
      // TODO(b/67934123) There should be a common interface to perform the dispatch.
      if (item instanceof DexType) {
        result.put(lookupType((DexType) item), entry.getBooleanValue());
      } else if (item instanceof DexMethod) {
        DexMethod method = (DexMethod) item;
        if (isContextFreeForMethod(method)) {
          result.put(lookupMethod(method), entry.getBooleanValue());
        } else {
          for (DexMethod candidate: lookupMethodInAllContexts(method)) {
            result.put(candidate, entry.getBooleanValue());
          }
        }
      } else if (item instanceof DexField) {
        result.put(lookupField((DexField) item), entry.getBooleanValue());
      } else {
        throw new Unreachable();
      }
    }
    return result;
  }

  public ImmutableSortedSet<DexMethod> rewriteMethodsWithRenamedSignature(Set<DexMethod> methods) {
    ImmutableSortedSet.Builder<DexMethod> builder =
        new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
    for (DexMethod method : methods) {
      builder.add(getRenamedMethodSignature(method));
    }
    return builder.build();
  }

  public ImmutableSortedSet<DexMethod> rewriteMethodsConservatively(Set<DexMethod> original) {
    ImmutableSortedSet.Builder<DexMethod> builder =
        new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
    if (isContextFreeForMethods()) {
      for (DexMethod item : original) {
        builder.add(lookupMethod(item));
      }
    } else {
      for (DexMethod item : original) {
        // Avoid using lookupMethodInAllContexts when possible.
        if (isContextFreeForMethod(item)) {
          builder.add(lookupMethod(item));
        } else {
          // The lense is context sensitive, but we do not have the context here. Therefore, we
          // conservatively look up the method in all contexts.
          builder.addAll(lookupMethodInAllContexts(item));
        }
      }
    }
    return builder.build();
  }

  private static class IdentityGraphLense extends GraphLense {

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      return field;
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      return originalField;
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod) {
      return originalMethod;
    }

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

    // Maps that store the original signature of fields and methods that have been affected by
    // vertical class merging. Needed to generate a correct Proguard map in the end.
    private final BiMap<DexField, DexField> originalFieldSignatures;
    private final BiMap<DexMethod, DexMethod> originalMethodSignatures;

    public NestedGraphLense(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLense previousLense,
        DexItemFactory dexItemFactory) {
      this.typeMap = typeMap.isEmpty() ? null : typeMap;
      this.methodMap = methodMap;
      this.fieldMap = fieldMap;
      this.originalFieldSignatures = originalFieldSignatures;
      this.originalMethodSignatures = originalMethodSignatures;
      this.previousLense = previousLense;
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      DexField originalField =
          originalFieldSignatures != null
              ? originalFieldSignatures.getOrDefault(field, field)
              : field;
      return previousLense.getOriginalFieldSignature(originalField);
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      DexMethod originalMethod =
          originalMethodSignatures != null
              ? originalMethodSignatures.getOrDefault(method, method)
              : method;
      return previousLense.getOriginalMethodSignature(originalMethod);
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      DexField renamedField =
          originalFieldSignatures != null
              ? originalFieldSignatures.inverse().getOrDefault(originalField, originalField)
              : originalField;
      return previousLense.getRenamedFieldSignature(renamedField);
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod) {
      DexMethod renamedMethod =
          originalMethodSignatures != null
              ? originalMethodSignatures.inverse().getOrDefault(originalMethod, originalMethod)
              : originalMethod;
      return previousLense.getRenamedMethodSignature(renamedMethod);
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
          newMethod, mapInvocationType(newMethod, method, context, previous.getType()));
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
