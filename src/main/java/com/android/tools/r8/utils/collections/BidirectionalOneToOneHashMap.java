// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.utils.IterableUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class BidirectionalOneToOneHashMap<K, V>
    extends BidirectionalManyToManyRepresentativeMap<K, V> implements Map<K, V> {

  private final BiMap<K, V> backing;

  public BidirectionalOneToOneHashMap() {
    this(HashBiMap.create());
  }

  public BidirectionalOneToOneHashMap(BiMap<K, V> backing) {
    this.backing = backing;
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

  public V forcePut(K key, V value) {
    return backing.forcePut(key, value);
  }

  @Override
  public V get(Object key) {
    return backing.get(key);
  }

  @Override
  public BiMap<K, V> getForwardBacking() {
    return backing;
  }

  @Override
  public BiMap<V, K> getInverseBacking() {
    return backing.inverse();
  }

  public BidirectionalOneToOneHashMap<V, K> getInverseOneToOneMap() {
    return new BidirectionalOneToOneHashMap<>(backing.inverse());
  }

  @Override
  public K getRepresentativeKey(V value) {
    return backing.inverse().get(value);
  }

  @Override
  public V getRepresentativeValue(K key) {
    return backing.get(key);
  }

  @Override
  public Iterable<K> getKeys(V value) {
    if (containsValue(value)) {
      return IterableUtils.singleton(getRepresentativeKey(value));
    }
    return IterableUtils.empty();
  }

  @Override
  public Iterable<V> getValues(K key) {
    if (containsKey(key)) {
      return IterableUtils.singleton(getRepresentativeValue(key));
    }
    return IterableUtils.empty();
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
    return backing.put(key, value);
  }

  public void putAll(BidirectionalOneToOneHashMap<K, V> map) {
    putAll(map.backing);
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
  public Collection<V> values() {
    return backing.values();
  }
}
