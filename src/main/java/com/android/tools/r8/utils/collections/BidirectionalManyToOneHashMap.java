// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BidirectionalManyToOneHashMap<K, V> implements MutableBidirectionalManyToOneMap<K, V> {

  private final Map<K, V> backing;
  private final Map<V, Set<K>> inverse;

  public static <K, V> BidirectionalManyToOneHashMap<K, V> newIdentityHashMap() {
    return new BidirectionalManyToOneHashMap<>(new IdentityHashMap<>(), new IdentityHashMap<>());
  }

  public static <K, V> BidirectionalManyToOneHashMap<K, V> newLinkedHashMap() {
    return new BidirectionalManyToOneHashMap<>(new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  protected BidirectionalManyToOneHashMap(Map<K, V> backing, Map<V, Set<K>> inverse) {
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
    backing.forEach(consumer);
  }

  @Override
  public void forEachKey(Consumer<? super K> consumer) {
    backing.keySet().forEach(consumer);
  }

  @Override
  public void forEachManyToOneMapping(BiConsumer<? super Set<K>, V> consumer) {
    inverse.forEach((value, keys) -> consumer.accept(keys, value));
  }

  @Override
  public void forEachValue(Consumer<? super V> consumer) {
    inverse.keySet().forEach(consumer);
  }

  @Override
  public V get(Object key) {
    return backing.get(key);
  }

  @Override
  public V getOrDefault(Object key, V value) {
    return backing.getOrDefault(key, value);
  }

  @Override
  public Map<K, V> getForwardMap() {
    return backing;
  }

  @Override
  public Set<K> keySet() {
    return backing.keySet();
  }

  @Override
  public Set<K> getKeys(V value) {
    return inverse.getOrDefault(value, Collections.emptySet());
  }

  @Override
  public Set<V> getValues(K key) {
    V value = get(key);
    return value != null ? Collections.singleton(value) : Collections.emptySet();
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public V remove(K key) {
    V value = backing.remove(key);
    if (value != null) {
      Set<K> keys = inverse.get(value);
      keys.remove(key);
      if (keys.isEmpty()) {
        inverse.remove(value);
      }
    }
    return value;
  }

  @Override
  public void removeAll(Iterable<K> keys) {
    keys.forEach(this::remove);
  }

  @Override
  public Set<K> removeValue(V value) {
    Set<K> keys = inverse.remove(value);
    if (keys == null) {
      return Collections.emptySet();
    }
    for (K key : keys) {
      V removedValue = backing.remove(key);
      assert removedValue == value;
    }
    return keys;
  }

  @Override
  public V put(K key, V value) {
    V old = remove(key);
    backing.put(key, value);
    inverse.computeIfAbsent(value, ignore -> new LinkedHashSet<>()).add(key);
    return old;
  }

  @Override
  public void put(Iterable<K> keys, V value) {
    keys.forEach(key -> put(key, value));
  }

  @Override
  public Set<V> values() {
    return inverse.keySet();
  }
}
