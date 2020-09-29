// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BidirectionalManyToOneMap<K, V> {

  private final Map<K, V> backing = new IdentityHashMap<>();
  private final Map<V, Set<K>> inverse = new IdentityHashMap<>();

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

  public void put(K key, V value) {
    backing.put(key, value);
    inverse.computeIfAbsent(value, ignore -> new LinkedHashSet<>()).add(key);
  }
}
