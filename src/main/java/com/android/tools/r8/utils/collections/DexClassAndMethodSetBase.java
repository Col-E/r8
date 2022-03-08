// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class DexClassAndMethodSetBase<T extends DexClassAndMethod> implements Iterable<T> {

  protected final Map<DexMethod, T> backing;
  protected final Supplier<? extends Map<DexMethod, T>> backingFactory;

  protected DexClassAndMethodSetBase(Supplier<? extends Map<DexMethod, T>> backingFactory) {
    this(backingFactory, backingFactory.get());
  }

  protected DexClassAndMethodSetBase(
      Supplier<? extends Map<DexMethod, T>> backingFactory, Map<DexMethod, T> backing) {
    this.backing = backing;
    this.backingFactory = backingFactory;
  }

  public boolean add(T method) {
    T existing = backing.put(method.getReference(), method);
    assert existing == null || existing.isStructurallyEqualTo(method);
    return existing == null;
  }

  public void addAll(Iterable<T> methods) {
    methods.forEach(this::add);
  }

  public T get(DexMethod method) {
    return backing.get(method);
  }

  public T getFirst() {
    return iterator().next();
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

  public void clear() {
    backing.clear();
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return backing.values().iterator();
  }

  public boolean remove(DexMethod method) {
    T existing = backing.remove(method);
    return existing != null;
  }

  public boolean remove(DexEncodedMethod method) {
    return remove(method.getReference());
  }

  public boolean removeIf(Predicate<? super T> predicate) {
    return backing.values().removeIf(predicate);
  }

  public int size() {
    return backing.size();
  }

  public Stream<T> stream() {
    return backing.values().stream();
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
}
