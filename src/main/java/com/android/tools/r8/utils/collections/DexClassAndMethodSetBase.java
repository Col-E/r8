// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class DexClassAndMethodSetBase<T extends DexClassAndMethod>
    implements Collection<T> {

  Map<DexMethod, T> backing;

  DexClassAndMethodSetBase() {
    this.backing = createBacking();
  }

  DexClassAndMethodSetBase(Map<DexMethod, T> backing) {
    this.backing = backing;
  }

  DexClassAndMethodSetBase(int capacity) {
    this.backing = createBacking(capacity);
  }

  abstract Map<DexMethod, T> createBacking();

  abstract Map<DexMethod, T> createBacking(int capacity);

  @Override
  public boolean add(T method) {
    T existing = backing.put(method.getReference(), method);
    assert existing == null || existing.isStructurallyEqualTo(method);
    return existing == null;
  }

  @Override
  public boolean addAll(Collection<? extends T> methods) {
    boolean changed = false;
    for (T method : methods) {
      changed |= add(method);
    }
    return changed;
  }

  public T get(DexMethod method) {
    return backing.get(method);
  }

  public T getFirst() {
    return iterator().next();
  }

  @Override
  public boolean contains(Object o) {
    if (o instanceof DexClassAndMethod) {
      DexClassAndMethod method = (DexClassAndMethod) o;
      return contains(method.getReference());
    }
    return false;
  }

  public boolean contains(DexMethod method) {
    return backing.containsKey(method);
  }

  public boolean contains(DexEncodedMethod method) {
    return backing.containsKey(method.getReference());
  }

  public boolean contains(T method) {
    return backing.containsKey(method.getReference());
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return Iterables.all(collection, this::contains);
  }

  @Override
  public void clear() {
    backing.clear();
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return backing.values().iterator();
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof DexClassAndMethod) {
      DexClassAndMethod method = (DexClassAndMethod) o;
      return remove(method.getReference());
    }
    return false;
  }

  public boolean remove(DexMethod method) {
    T existing = backing.remove(method);
    return existing != null;
  }

  public boolean remove(DexEncodedMethod method) {
    return remove(method.getReference());
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    boolean changed = false;
    for (Object o : collection) {
      changed |= remove(o);
    }
    return changed;
  }

  @Override
  public boolean removeIf(Predicate<? super T> predicate) {
    return backing.values().removeIf(predicate);
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    return backing.values().retainAll(collection);
  }

  @Override
  public int size() {
    return backing.size();
  }

  @Override
  public Stream<T> stream() {
    return backing.values().stream();
  }

  @Override
  public Object[] toArray() {
    return backing.values().toArray();
  }

  @Override
  public <S> S[] toArray(S[] ss) {
    return backing.values().toArray(ss);
  }

  public Collection<T> toCollection() {
    return backing.values();
  }

  public Set<DexEncodedMethod> toDefinitionSet() {
    assert backing instanceof IdentityHashMap;
    return toDefinitionSet(SetUtils::newIdentityHashSet);
  }

  public Set<DexEncodedMethod> toDefinitionSet(IntFunction<Set<DexEncodedMethod>> factory) {
    Set<DexEncodedMethod> definitions = factory.apply(size());
    forEach(method -> definitions.add(method.getDefinition()));
    return definitions;
  }

  public void trimCapacityIfSizeLessThan(int expectedSize) {
    if (size() < expectedSize) {
      Map<DexMethod, T> newBacking = createBacking(size());
      newBacking.putAll(backing);
      backing = newBacking;
    }
  }
}
