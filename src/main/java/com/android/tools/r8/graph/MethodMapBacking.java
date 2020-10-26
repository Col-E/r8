// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceRBTreeMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class MethodMapBacking extends MethodCollectionBacking {

  private Object2ReferenceMap<Wrapper<DexMethod>, DexEncodedMethod> methodMap;

  public MethodMapBacking() {
    this(createMap());
  }

  private MethodMapBacking(Object2ReferenceMap<Wrapper<DexMethod>, DexEncodedMethod> methodMap) {
    this.methodMap = methodMap;
  }

  public static MethodMapBacking createSorted() {
    Comparator<Wrapper<DexMethod>> comparator = (x, y) -> x.get().slowCompareTo(y.get());
    return new MethodMapBacking(new Object2ReferenceRBTreeMap<>(comparator));
  }

  private static Object2ReferenceMap<Wrapper<DexMethod>, DexEncodedMethod> createMap() {
    // Maintain a linked map so the output order remains a deterministic function of the input.
    return new Object2ReferenceLinkedOpenHashMap<>();
  }

  private static Object2ReferenceMap<Wrapper<DexMethod>, DexEncodedMethod> createMap(int capacity) {
    // Maintain a linked map so the output order remains a deterministic function of the input.
    return new Object2ReferenceLinkedOpenHashMap<>(capacity);
  }

  private Wrapper<DexMethod> wrap(DexMethod method) {
    return MethodSignatureEquivalence.get().wrap(method);
  }

  private void replace(Wrapper<DexMethod> existingKey, DexEncodedMethod method) {
    if (existingKey.get().match(method)) {
      methodMap.put(existingKey, method);
    } else {
      methodMap.remove(existingKey);
      methodMap.put(wrap(method.method), method);
    }
  }

  @Override
  boolean verify() {
    methodMap.forEach(
        (key, method) -> {
          assert key.get().match(method);
        });
    return true;
  }

  @Override
  public int numberOfDirectMethods() {
    return numberOfMethodsMatching(this::belongsToDirectPool);
  }

  @Override
  public int numberOfVirtualMethods() {
    return numberOfMethodsMatching(this::belongsToVirtualPool);
  }

  private int numberOfMethodsMatching(Predicate<DexEncodedMethod> predicate) {
    int count = 0;
    for (DexEncodedMethod method : methodMap.values()) {
      if (predicate.test(method)) {
        count++;
      }
    }
    return count;
  }

  @Override
  int size() {
    return methodMap.size();
  }

  @Override
  TraversalContinuation traverse(Function<DexEncodedMethod, TraversalContinuation> fn) {
    for (Entry<Wrapper<DexMethod>, DexEncodedMethod> entry : methodMap.object2ReferenceEntrySet()) {
      TraversalContinuation result = fn.apply(entry.getValue());
      if (result.shouldBreak()) {
        return result;
      }
    }
    return TraversalContinuation.CONTINUE;
  }

  @Override
  Iterable<DexEncodedMethod> methods() {
    return methodMap.values();
  }

  @Override
  Iterable<DexEncodedMethod> directMethods() {
    return () -> IteratorUtils.filter(methodMap.values().iterator(), this::belongsToDirectPool);
  }

  @Override
  Iterable<DexEncodedMethod> virtualMethods() {
    return () -> IteratorUtils.filter(methodMap.values().iterator(), this::belongsToVirtualPool);
  }

  @Override
  DexEncodedMethod getMethod(DexMethod method) {
    return methodMap.get(wrap(method));
  }

  private DexEncodedMethod getMethod(Predicate<DexEncodedMethod> predicate) {
    Box<DexEncodedMethod> found = new Box<>();
    traverse(
        method -> {
          if (predicate.test(method)) {
            found.set(method);
            return TraversalContinuation.BREAK;
          }
          return TraversalContinuation.CONTINUE;
        });
    return found.get();
  }

  @Override
  DexEncodedMethod getDirectMethod(DexMethod method) {
    DexEncodedMethod definition = getMethod(method);
    return definition != null && belongsToDirectPool(definition) ? definition : null;
  }

  @Override
  DexEncodedMethod getDirectMethod(Predicate<DexEncodedMethod> predicate) {
    Predicate<DexEncodedMethod> isDirect = this::belongsToDirectPool;
    return getMethod(isDirect.and(predicate));
  }

  @Override
  DexEncodedMethod getVirtualMethod(DexMethod method) {
    DexEncodedMethod definition = getMethod(method);
    return definition != null && belongsToVirtualPool(definition) ? definition : null;
  }

  @Override
  DexEncodedMethod getVirtualMethod(Predicate<DexEncodedMethod> predicate) {
    Predicate<DexEncodedMethod> isVirtual = this::belongsToVirtualPool;
    return getMethod(isVirtual.and(predicate));
  }

  @Override
  void addMethod(DexEncodedMethod method) {
    Wrapper<DexMethod> key = wrap(method.method);
    DexEncodedMethod old = methodMap.put(key, method);
    assert old == null;
  }

  @Override
  void addDirectMethod(DexEncodedMethod method) {
    assert belongsToDirectPool(method);
    addMethod(method);
  }

  @Override
  void addVirtualMethod(DexEncodedMethod method) {
    assert belongsToVirtualPool(method);
    addMethod(method);
  }

  @Override
  void addDirectMethods(Collection<DexEncodedMethod> methods) {
    for (DexEncodedMethod method : methods) {
      addDirectMethod(method);
    }
  }

  @Override
  void addVirtualMethods(Collection<DexEncodedMethod> methods) {
    for (DexEncodedMethod method : methods) {
      addVirtualMethod(method);
    }
  }

  @Override
  void clearDirectMethods() {
    methodMap.values().removeIf(this::belongsToDirectPool);
  }

  @Override
  void clearVirtualMethods() {
    methodMap.values().removeIf(this::belongsToVirtualPool);
  }

  @Override
  DexEncodedMethod removeMethod(DexMethod method) {
    return methodMap.remove(wrap(method));
  }

  @Override
  void removeMethods(Set<DexEncodedMethod> methods) {
    methods.forEach(method -> methodMap.remove(wrap(method.getReference())));
  }

  @Override
  void setDirectMethods(DexEncodedMethod[] methods) {
    if ((methods == null || methods.length == 0) && methodMap.isEmpty()) {
      return;
    }
    if (methods == null) {
      methods = DexEncodedMethod.EMPTY_ARRAY;
    }
    Object2ReferenceMap<Wrapper<DexMethod>, DexEncodedMethod> newMap =
        createMap(size() + methods.length);
    forEachMethod(
        method -> {
          if (belongsToVirtualPool(method)) {
            newMap.put(wrap(method.method), method);
          }
        });
    for (DexEncodedMethod method : methods) {
      assert belongsToDirectPool(method);
      newMap.put(wrap(method.method), method);
    }
    methodMap = newMap;
  }

  @Override
  void setVirtualMethods(DexEncodedMethod[] methods) {
    if ((methods == null || methods.length == 0) && methodMap.isEmpty()) {
      return;
    }
    if (methods == null) {
      methods = DexEncodedMethod.EMPTY_ARRAY;
    }
    Object2ReferenceMap<Wrapper<DexMethod>, DexEncodedMethod> newMap =
        createMap(size() + methods.length);
    forEachMethod(
        method -> {
          if (belongsToDirectPool(method)) {
            newMap.put(wrap(method.method), method);
          }
        });
    for (DexEncodedMethod method : methods) {
      assert belongsToVirtualPool(method);
      newMap.put(wrap(method.method), method);
    }
    methodMap = newMap;
  }

  @Override
  void replaceMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    // The code assumes that when replacement.apply(method) is called, the map is up-to-date with
    // the previously replaced methods. We therefore cannot postpone the map updates to the end of
    // the method.
    ArrayList<DexEncodedMethod> initialValues = new ArrayList<>(methodMap.values());
    for (DexEncodedMethod method : initialValues) {
      DexEncodedMethod newMethod = replacement.apply(method);
      if (newMethod != method) {
        removeMethod(method.method);
        addMethod(newMethod);
      }
    }
  }

  @Override
  void replaceDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    replaceMethods(method -> belongsToDirectPool(method) ? replacement.apply(method) : method);
  }

  @Override
  void replaceVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    replaceMethods(method -> belongsToVirtualPool(method) ? replacement.apply(method) : method);
  }

  @Override
  void replaceAllDirectMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    List<DexEncodedMethod> oldMethods = Lists.newArrayList(directMethods());
    clearDirectMethods();
    List<DexEncodedMethod> newMethods = new ArrayList<>(oldMethods.size());
    for (DexEncodedMethod method : oldMethods) {
      newMethods.add(replacement.apply(method));
    }
    addDirectMethods(newMethods);
  }

  @Override
  void replaceAllVirtualMethods(Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    List<DexEncodedMethod> oldMethods = Lists.newArrayList(virtualMethods());
    clearVirtualMethods();
    List<DexEncodedMethod> newMethods = new ArrayList<>(oldMethods.size());
    for (DexEncodedMethod method : oldMethods) {
      newMethods.add(replacement.apply(method));
    }
    addVirtualMethods(newMethods);
  }

  @Override
  DexEncodedMethod replaceDirectMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    return replaceMethod(method, replacement, this::belongsToDirectPool);
  }

  @Override
  DexEncodedMethod replaceVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    return replaceMethod(method, replacement, this::belongsToVirtualPool);
  }

  private DexEncodedMethod replaceMethod(
      DexMethod method,
      Function<DexEncodedMethod, DexEncodedMethod> replacement,
      Predicate<DexEncodedMethod> predicate) {
    Wrapper<DexMethod> key = wrap(method);
    DexEncodedMethod existing = methodMap.get(key);
    if (existing == null || !predicate.test(existing)) {
      return null;
    }
    DexEncodedMethod newMethod = replacement.apply(existing);
    assert predicate.test(newMethod);
    replace(key, newMethod);
    return newMethod;
  }

  @Override
  DexEncodedMethod replaceDirectMethodWithVirtualMethod(
      DexMethod method, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    Wrapper<DexMethod> key = wrap(method);
    DexEncodedMethod existing = methodMap.get(key);
    if (existing == null || belongsToVirtualPool(existing)) {
      return null;
    }
    DexEncodedMethod newMethod = replacement.apply(existing);
    assert belongsToVirtualPool(newMethod);
    replace(key, newMethod);
    return newMethod;
  }

  @Override
  void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    // This is a no-op as the virtualizer has modified the encoded method bits.
    assert verifyVirtualizedMethods(privateInstanceMethods);
  }

  private boolean verifyVirtualizedMethods(Set<DexEncodedMethod> methods) {
    for (DexEncodedMethod method : methods) {
      assert belongsToVirtualPool(method);
      assert methodMap.get(wrap(method.method)) == method;
    }
    return true;
  }
}
