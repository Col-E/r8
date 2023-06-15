// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.utils.TriConsumer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BidirectionalOneToOneHashMap<K, V>
    implements MutableBidirectionalOneToOneMap<K, V>, Map<K, V> {

  private final BiMap<K, V> backing;

  public BidirectionalOneToOneHashMap() {
    this(HashBiMap.create());
  }

  public BidirectionalOneToOneHashMap(BiMap<K, V> backing) {
    this.backing = backing;
  }

  public BidirectionalOneToOneHashMap(Map<K, V> backing) {
    this(HashBiMap.create(backing));
  }

  @Override
  public void clear() {
    backing.clear();
  }

  @Override
  public boolean containsKey(Object key) {
    return backing.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return backing.containsValue(value);
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return backing.entrySet();
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
    backing.forEach((key, value) -> consumer.accept(Collections.singleton(key), value));
  }

  @Override
  public void forEachManyToOneMapping(TriConsumer<? super Set<K>, V, K> consumer) {
    backing.forEach((key, value) -> consumer.accept(Collections.singleton(key), value, key));
  }

  @Override
  public void forEachValue(Consumer<? super V> consumer) {
    backing.values().forEach(consumer);
  }

  @Override
  public V get(Object key) {
    return backing.get(key);
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    V value = get(key);
    return value != null ? value : defaultValue;
  }

  @Override
  public K getKey(V value) {
    return backing.inverse().get(value);
  }

  @Override
  public BiMap<K, V> getForwardMap() {
    return backing;
  }

  @Override
  public BidirectionalOneToOneHashMap<V, K> getInverseOneToOneMap() {
    return new BidirectionalOneToOneHashMap<>(backing.inverse());
  }

  @Override
  public boolean hasExplicitRepresentativeKey(V value) {
    assert containsValue(value);
    return true;
  }

  @Override
  public K getRepresentativeKey(V value) {
    return getKey(value);
  }

  @Override
  public V getRepresentativeValue(K key) {
    return get(key);
  }

  @Override
  public Set<K> getKeys(V value) {
    if (containsValue(value)) {
      return Collections.singleton(getRepresentativeKey(value));
    }
    return Collections.emptySet();
  }

  @Override
  public Set<V> getValues(K key) {
    if (containsKey(key)) {
      return Collections.singleton(getRepresentativeValue(key));
    }
    return Collections.emptySet();
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Set<K> keySet() {
    return backing.keySet();
  }

  @Override
  public V put(K key, V value) {
    return backing.forcePut(key, value);
  }

  @Override
  public void putAll(BidirectionalManyToManyMap<K, V> map) {
    map.forEach(this::put);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    backing.putAll(map);
  }

  @Override
  public V remove(Object key) {
    return backing.remove(key);
  }

  @Override
  public int size() {
    return backing.size();
  }

  @Override
  public Set<V> values() {
    return backing.values();
  }
}
