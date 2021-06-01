// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.google.common.collect.Streams;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class BidirectionalOneToManyRepresentativeHashMap<K, V>
    extends BidirectionalOneToManyHashMap<K, V>
    implements MutableBidirectionalOneToManyRepresentativeMap<K, V> {

  private final Map<K, V> representatives = new IdentityHashMap<>();

  @Override
  public void clear() {
    super.clear();
    representatives.clear();
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
    Set<V> values = getValues(key);
    if (!values.isEmpty()) {
      return values.size() == 1 ? values.iterator().next() : representatives.get(key);
    }
    return null;
  }

  @Override
  public Set<V> remove(K key) {
    Set<V> values = super.remove(key);
    removeRepresentativeFor(key);
    return values;
  }

  @Override
  public void removeAll(Iterable<K> keys) {
    super.removeAll(keys);
    assert Streams.stream(keys).noneMatch(representatives::containsKey);
  }

  @Override
  public V removeRepresentativeFor(K key) {
    return representatives.remove(key);
  }

  @Override
  public K removeValue(V value) {
    K key = super.removeValue(value);
    if (getValues(key).size() <= 1 || getRepresentativeValue(key) == value) {
      removeRepresentativeFor(key);
    }
    return key;
  }

  @Override
  public void setRepresentative(K key, V representative) {
    assert getValues(key).size() > 1;
    assert getValues(key).contains(representative);
    representatives.put(key, representative);
  }
}
