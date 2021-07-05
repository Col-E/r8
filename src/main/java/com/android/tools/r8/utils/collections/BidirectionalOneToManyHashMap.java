// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BidirectionalOneToManyHashMap<K, V> implements MutableBidirectionalOneToManyMap<K, V> {

  private final Map<K, Set<V>> backing;
  private final Map<V, K> inverse;

  public BidirectionalOneToManyHashMap() {
    this(new IdentityHashMap<>(), new IdentityHashMap<>());
  }

  private BidirectionalOneToManyHashMap(Map<K, Set<V>> backing, Map<V, K> inverse) {
    this.backing = backing;
    this.inverse = inverse;
  }

  @Override
  public void clear() {
    backing.clear();
    inverse.clear();
  }

  @Override
  public boolean containsKey(K key) {
    return backing.containsKey(key);
  }

  @Override
  public boolean containsValue(V value) {
    return inverse.containsKey(value);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> consumer) {
    backing.forEach((key, values) -> values.forEach(value -> consumer.accept(key, value)));
  }

  @Override
  public void forEachKey(Consumer<? super K> consumer) {
    backing.keySet().forEach(consumer);
  }

  @Override
  public void forEachOneToManyMapping(BiConsumer<K, Set<V>> consumer) {
    backing.forEach(consumer);
  }

  @Override
  public void forEachValue(Consumer<? super V> consumer) {
    inverse.keySet().forEach(consumer);
  }

  @Override
  public Set<V> get(Object key) {
    return backing.get(key);
  }

  @Override
  public Set<V> getOrDefault(Object key, Set<V> value) {
    return backing.getOrDefault(key, value);
  }

  @Override
  public K getKey(V value) {
    return inverse.get(value);
  }

  @Override
  public K getKeyOrDefault(V value, K defaultValue) {
    return inverse.getOrDefault(value, defaultValue);
  }

  @Override
  public Set<K> getKeys(V value) {
    K key = inverse.get(value);
    return key != null ? Collections.singleton(key) : Collections.emptySet();
  }

  @Override
  public Set<V> getValues(K key) {
    return getOrDefault(key, Collections.emptySet());
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  public Set<K> keySet() {
    return backing.keySet();
  }

  @Override
  public Set<V> remove(K key) {
    Set<V> values = backing.remove(key);
    if (values == null) {
      return Collections.emptySet();
    }
    for (V value : values) {
      K removedKey = inverse.remove(value);
      assert removedKey == key;
    }
    return values;
  }

  @Override
  public void removeAll(Iterable<K> keys) {
    keys.forEach(this::remove);
  }

  @Override
  public K removeValue(V value) {
    K key = inverse.remove(value);
    if (key != null) {
      Set<V> values = backing.get(key);
      values.remove(value);
      if (values.isEmpty()) {
        backing.remove(key);
      }
    }
    return key;
  }

  @Override
  public void put(K key, V value) {
    removeValue(value);
    backing.computeIfAbsent(key, ignore -> new LinkedHashSet<>()).add(value);
    inverse.put(value, key);
  }

  @Override
  public void put(K key, Set<V> values) {
    values.forEach(value -> put(key, value));
  }
}
