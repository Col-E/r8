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

public class BidirectionalManyToOneMap<K, V> {

  private final Map<K, V> backing;
  private final Map<V, Set<K>> inverse;

  public BidirectionalManyToOneMap() {
    this(new IdentityHashMap<>(), new IdentityHashMap<>());
  }

  private BidirectionalManyToOneMap(Map<K, V> backing, Map<V, Set<K>> inverse) {
    this.backing = backing;
    this.inverse = inverse;
  }

  public static <K, V> BidirectionalManyToOneMap<K, V> empty() {
    return new BidirectionalManyToOneMap<>(Collections.emptyMap(), Collections.emptyMap());
  }

  public boolean containsKey(K key) {
    return backing.containsKey(key);
  }

  public boolean containsValue(V value) {
    return inverse.containsKey(value);
  }

  public void forEach(BiConsumer<Set<K>, V> consumer) {
    inverse.forEach((value, keys) -> consumer.accept(keys, value));
  }

  public V get(K key) {
    return backing.get(key);
  }

  public V getOrDefault(K key, V value) {
    return backing.getOrDefault(key, value);
  }

  public Map<K, V> getForwardMap() {
    return backing;
  }

  public Set<K> keySet() {
    return backing.keySet();
  }

  public boolean hasKey(K key) {
    return backing.containsKey(key);
  }

  public boolean hasValue(V value) {
    return inverse.containsKey(value);
  }

  public Set<K> getKeys(V value) {
    return inverse.getOrDefault(value, Collections.emptySet());
  }

  public Set<K> getKeysOrNull(V value) {
    return inverse.get(value);
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  public void remove(K key) {
    V value = backing.remove(key);
    if (value != null) {
      Set<K> keys = inverse.get(value);
      keys.remove(key);
      if (keys.isEmpty()) {
        inverse.remove(value);
      }
    }
  }

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

  public void put(K key, V value) {
    remove(key);
    backing.put(key, value);
    inverse.computeIfAbsent(value, ignore -> new LinkedHashSet<>()).add(key);
  }

  public Set<V> values() {
    return inverse.keySet();
  }
}
