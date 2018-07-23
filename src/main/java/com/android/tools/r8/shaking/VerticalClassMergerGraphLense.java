// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
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
public class VerticalClassMergerGraphLense extends NestedGraphLense {
  private final AppInfo appInfo;

  private final Set<DexMethod> mergedMethods;
  private final Map<DexType, Map<DexMethod, GraphLenseLookupResult>>
      contextualVirtualToDirectMethodMaps;

  public VerticalClassMergerGraphLense(
      AppInfo appInfo,
      Map<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      Set<DexMethod> mergedMethods,
      Map<DexType, Map<DexMethod, GraphLenseLookupResult>> contextualVirtualToDirectMethodMaps,
      BiMap<DexField, DexField> originalFieldSignatures,
      BiMap<DexMethod, DexMethod> originalMethodSignatures,
      GraphLense previousLense) {
    super(
        ImmutableMap.of(),
        methodMap,
        fieldMap,
        originalFieldSignatures,
        originalMethodSignatures,
        previousLense,
        appInfo.dexItemFactory);
    this.appInfo = appInfo;
    this.mergedMethods = mergedMethods;
    this.contextualVirtualToDirectMethodMaps = contextualVirtualToDirectMethodMaps;
  }

  @Override
  public GraphLenseLookupResult lookupMethod(
      DexMethod method, DexEncodedMethod context, Type type) {
    assert isContextFreeForMethod(method) || (context != null && type != null);
    GraphLenseLookupResult previous = previousLense.lookupMethod(method, context, type);
    if (previous.getType() == Type.SUPER && !mergedMethods.contains(context.method)) {
      Map<DexMethod, GraphLenseLookupResult> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.get(context.method.holder);
      if (virtualToDirectMethodMap != null) {
        GraphLenseLookupResult lookup = virtualToDirectMethodMap.get(previous.getMethod());
        if (lookup != null) {
          // If the super class A of the enclosing class B (i.e., context.method.holder)
          // has been merged into B during vertical class merging, and this invoke-super instruction
          // was resolving to a method in A, then the target method has been changed to a direct
          // method and moved into B, so that we need to use an invoke-direct instruction instead of
          // invoke-super (or invoke-static, if the method was originally a default interface
          // method).
          return lookup;
        }
      }
    }
    return super.lookupMethod(previous.getMethod(), context, previous.getType());
  }

  @Override
  protected Type mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, DexEncodedMethod context, Type type) {
    return super.mapVirtualInterfaceInvocationTypes(
        appInfo, newMethod, originalMethod, context, type);
  }

  @Override
  public Set<DexMethod> lookupMethodInAllContexts(DexMethod method) {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    for (DexMethod previous : previousLense.lookupMethodInAllContexts(method)) {
      builder.add(methodMap.getOrDefault(previous, previous));
      for (Map<DexMethod, GraphLenseLookupResult> virtualToDirectMethodMap :
          contextualVirtualToDirectMethodMaps.values()) {
        GraphLenseLookupResult lookup = virtualToDirectMethodMap.get(previous);
        if (lookup != null) {
          builder.add(lookup.getMethod());
        }
      }
    }
    return builder.build();
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
    for (Map<DexMethod, GraphLenseLookupResult> virtualToDirectMethodMap :
        contextualVirtualToDirectMethodMaps.values()) {
      if (virtualToDirectMethodMap.containsKey(previous)) {
        return false;
      }
    }
    return true;
  }

  public static class Builder {

    protected final BiMap<DexField, DexField> fieldMap = HashBiMap.create();
    protected final Map<DexMethod, DexMethod> methodMap = new HashMap<>();
    private final ImmutableSet.Builder<DexMethod> mergedMethodsBuilder = ImmutableSet.builder();
    private final Map<DexType, Map<DexMethod, GraphLenseLookupResult>>
        contextualVirtualToDirectMethodMaps = new HashMap<>();

    private final Map<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

    public GraphLense build(
        GraphLense previousLense, Map<DexType, DexType> mergedClasses, AppInfo appInfo) {
      if (fieldMap.isEmpty()
          && methodMap.isEmpty()
          && contextualVirtualToDirectMethodMaps.isEmpty()) {
        return previousLense;
      }
      Map<DexProto, DexProto> cache = new HashMap<>();
      BiMap<DexField, DexField> originalFieldSignatures = fieldMap.inverse();
      return new VerticalClassMergerGraphLense(
          appInfo,
          fieldMap,
          methodMap,
          getMergedMethodSignaturesAfterClassMerging(
              mergedMethodsBuilder.build(), mergedClasses, appInfo.dexItemFactory, cache),
          contextualVirtualToDirectMethodMaps,
          getOriginalFieldSignaturesAfterClassMerging(
              originalFieldSignatures, mergedClasses, appInfo.dexItemFactory),
          getOriginalMethodSignaturesAfterClassMerging(
              originalMethodSignatures, mergedClasses, appInfo.dexItemFactory, cache),
          previousLense);
    }

    // After we have recorded that a method "a.b.c.Foo;->m(A, B, C)V" was merged into another class,
    // it could be that the class B was merged into its subclass B'. In that case we update the
    // signature to "a.b.c.Foo;->m(A, B', C)V".
    private static Set<DexMethod> getMergedMethodSignaturesAfterClassMerging(
        Set<DexMethod> mergedMethods,
        Map<DexType, DexType> mergedClasses,
        DexItemFactory dexItemFactory,
        Map<DexProto, DexProto> cache) {
      ImmutableSet.Builder<DexMethod> result = ImmutableSet.builder();
      for (DexMethod signature : mergedMethods) {
        result.add(
            getMethodSignatureAfterClassMerging(signature, mergedClasses, dexItemFactory, cache));
      }
      return result.build();
    }

    private static BiMap<DexField, DexField> getOriginalFieldSignaturesAfterClassMerging(
        Map<DexField, DexField> originalFieldSignatures,
        Map<DexType, DexType> mergedClasses,
        DexItemFactory dexItemFactory) {
      ImmutableBiMap.Builder<DexField, DexField> result = ImmutableBiMap.builder();
      for (Map.Entry<DexField, DexField> entry : originalFieldSignatures.entrySet()) {
        result.put(
            getFieldSignatureAfterClassMerging(entry.getKey(), mergedClasses, dexItemFactory),
            entry.getValue());
      }
      return result.build();
    }

    private static BiMap<DexMethod, DexMethod> getOriginalMethodSignaturesAfterClassMerging(
        Map<DexMethod, DexMethod> originalMethodSignatures,
        Map<DexType, DexType> mergedClasses,
        DexItemFactory dexItemFactory,
        Map<DexProto, DexProto> cache) {
      ImmutableBiMap.Builder<DexMethod, DexMethod> result = ImmutableBiMap.builder();
      for (Map.Entry<DexMethod, DexMethod> entry : originalMethodSignatures.entrySet()) {
        result.put(
            getMethodSignatureAfterClassMerging(
                entry.getKey(), mergedClasses, dexItemFactory, cache),
            entry.getValue());
      }
      return result.build();
    }

    private static DexField getFieldSignatureAfterClassMerging(
        DexField signature, Map<DexType, DexType> mergedClasses, DexItemFactory dexItemFactory) {
      DexType newClass = mergedClasses.getOrDefault(signature.clazz, signature.clazz);
      DexType newType = mergedClasses.getOrDefault(signature.type, signature.type);
      if (signature.clazz.equals(newClass) && signature.type.equals(newType)) {
        return signature;
      }
      return dexItemFactory.createField(newClass, newType, signature.name);
    }

    private static DexMethod getMethodSignatureAfterClassMerging(
        DexMethod signature,
        Map<DexType, DexType> mergedClasses,
        DexItemFactory dexItemFactory,
        Map<DexProto, DexProto> cache) {
      DexType newHolder = mergedClasses.getOrDefault(signature.holder, signature.holder);
      DexProto newProto =
          dexItemFactory.applyClassMappingToProto(
              signature.proto, type -> mergedClasses.getOrDefault(type, type), cache);
      if (signature.holder.equals(newHolder) && signature.proto.equals(newProto)) {
        return signature;
      }
      return dexItemFactory.createMethod(newHolder, newProto, signature.name);
    }

    public boolean hasMappingForSignatureInContext(DexType context, DexMethod signature) {
      Map<DexMethod, GraphLenseLookupResult> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.get(context);
      if (virtualToDirectMethodMap != null) {
        return virtualToDirectMethodMap.containsKey(signature);
      }
      return false;
    }

    public void markMethodAsMerged(DexMethod method) {
      mergedMethodsBuilder.add(method);
    }

    public void map(DexField from, DexField to) {
      fieldMap.put(from, to);
    }

    public void map(DexMethod from, DexMethod to) {
      methodMap.put(from, to);
    }

    public void recordMove(DexMethod from, DexMethod to) {
      originalMethodSignatures.put(to, from);
    }

    public void mapVirtualMethodToDirectInType(
        DexMethod from, GraphLenseLookupResult to, DexType type) {
      Map<DexMethod, GraphLenseLookupResult> virtualToDirectMethodMap =
          contextualVirtualToDirectMethodMaps.computeIfAbsent(type, key -> new HashMap<>());
      virtualToDirectMethodMap.put(from, to);
    }

    public void merge(VerticalClassMergerGraphLense.Builder builder) {
      fieldMap.putAll(builder.fieldMap);
      methodMap.putAll(builder.methodMap);
      mergedMethodsBuilder.addAll(builder.mergedMethodsBuilder.build());
      originalMethodSignatures.putAll(builder.originalMethodSignatures);
      for (DexType context : builder.contextualVirtualToDirectMethodMaps.keySet()) {
        Map<DexMethod, GraphLenseLookupResult> current =
            contextualVirtualToDirectMethodMaps.get(context);
        Map<DexMethod, GraphLenseLookupResult> other =
            builder.contextualVirtualToDirectMethodMaps.get(context);
        if (current != null) {
          current.putAll(other);
        } else {
          contextualVirtualToDirectMethodMaps.put(context, other);
        }
      }
    }
  }
}
