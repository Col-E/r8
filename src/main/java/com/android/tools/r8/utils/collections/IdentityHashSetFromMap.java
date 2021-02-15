// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.ProgramDerivedContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class IdentityHashSetFromMap<K, V> implements Set<V> {

  private final Map<K, V> backing = new IdentityHashMap<>();
  private final Function<V, K> valueToKeyMapping;

  public IdentityHashSetFromMap(Function<V, K> valueToKeyMapping) {
    this.valueToKeyMapping = valueToKeyMapping;
  }

  public static Set<ProgramDerivedContext> newProgramDerivedContextSet() {
    return new IdentityHashSetFromMap<>(context -> context.getContext().getReference());
  }

  @Override
  public int size() {
    return backing.size();
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean contains(Object o) {
    return backing.containsKey(valueToKeyMapping.apply((V) o));
  }

  @Override
  public Iterator<V> iterator() {
    return backing.values().iterator();
  }

  @Override
  public Object[] toArray() {
    return backing.values().toArray();
  }

  @Override
  public <T> T[] toArray(T[] ts) {
    return backing.values().toArray(ts);
  }

  @Override
  public boolean add(V v) {
    return backing.put(valueToKeyMapping.apply(v), v) == null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean remove(Object o) {
    return backing.remove(valueToKeyMapping.apply((V) o)) != null;
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return backing.values().containsAll(collection);
  }

  @Override
  public boolean addAll(Collection<? extends V> collection) {
    boolean changed = false;
    for (V element : collection) {
      changed |= add(element);
    }
    return changed;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean retainAll(Collection<?> collection) {
    Collection<Object> found = new ArrayList<>(collection.size());
    for (Object element : collection) {
      if (contains(element)) {
        found.add(element);
      }
    }
    if (found.size() < size()) {
      clear();
      addAll((Collection<V>) found);
      return true;
    }
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    boolean changed = false;
    for (Object element : collection) {
      changed |= remove(element);
    }
    return changed;
  }

  @Override
  public void clear() {
    backing.clear();
  }
}
