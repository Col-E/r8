// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.utils.TriConsumer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EmptyBidirectionalOneToOneMap<K, V>
    implements BidirectionalOneToOneMap<K, V>,
        BidirectionalManyToOneRepresentativeMap<K, V>,
        BidirectionalManyToManyRepresentativeMap<K, V> {

  @Override
  public boolean containsKey(K key) {
    return false;
  }

  @Override
  public boolean containsValue(V value) {
    return false;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> consumer) {
    // Intentionally empty.
  }

  @Override
  public void forEachKey(Consumer<? super K> consumer) {
    // Intentionally empty.
  }

  @Override
  public void forEachManyToOneMapping(BiConsumer<? super Set<K>, V> consumer) {
    // Intentionally empty.
  }

  @Override
  public void forEachManyToOneMapping(TriConsumer<? super Set<K>, V, K> consumer) {
    // Intentionally empty.
  }

  @Override
  public void forEachValue(Consumer<? super V> consumer) {
    // Intentionally empty.
  }

  @Override
  public V get(Object key) {
    return null;
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return defaultValue;
  }

  @Override
  public K getKey(V value) {
    return null;
  }

  @Override
  public BiMap<K, V> getForwardMap() {
    return HashBiMap.create();
  }

  @Override
  public boolean hasExplicitRepresentativeKey(V value) {
    return false;
  }

  @Override
  public K getRepresentativeKey(V value) {
    return null;
  }

  @Override
  public V getRepresentativeValue(K key) {
    return null;
  }

  @Override
  public Set<K> getKeys(V value) {
    return Collections.emptySet();
  }

  @Override
  public Set<V> getValues(K key) {
    return Collections.emptySet();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public Set<K> keySet() {
    return Collections.emptySet();
  }

  @Override
  public Set<V> values() {
    return Collections.emptySet();
  }

  @Override
  public BidirectionalOneToOneMap<V, K> getInverseOneToOneMap() {
    return new EmptyBidirectionalOneToOneMap<>();
  }
}
