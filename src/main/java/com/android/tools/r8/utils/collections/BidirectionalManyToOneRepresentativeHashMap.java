// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.utils.TriConsumer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class BidirectionalManyToOneRepresentativeHashMap<K, V>
    extends BidirectionalManyToOneHashMap<K, V>
    implements MutableBidirectionalManyToOneRepresentativeMap<K, V> {

  private final Map<V, K> representatives;

  public static <K, V> BidirectionalManyToOneRepresentativeHashMap<K, V> newIdentityHashMap() {
    return new BidirectionalManyToOneRepresentativeHashMap<>(
        new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());
  }

  private BidirectionalManyToOneRepresentativeHashMap(
      Map<K, V> backing, Map<V, Set<K>> inverse, Map<V, K> representatives) {
    super(backing, inverse);
    this.representatives = representatives;
  }

  @Override
  public void clear() {
    super.clear();
    representatives.clear();
  }

  @Override
  public void forEachManyToOneMapping(TriConsumer<? super Set<K>, V, K> consumer) {
    forEachManyToOneMapping(
        (keys, value) -> consumer.accept(keys, value, getRepresentativeKey(value)));
  }

  @Override
  public K removeRepresentativeFor(V value) {
    return representatives.remove(value);
  }

  @Override
  public void setRepresentative(V value, K representative) {
    representatives.put(value, representative);
  }

  @Override
  public boolean hasExplicitRepresentativeKey(V value) {
    return representatives.containsKey(value);
  }

  @Override
  public K getRepresentativeKey(V value) {
    Set<K> keys = getKeys(value);
    if (!keys.isEmpty()) {
      if (keys.size() == 1) {
        return keys.iterator().next();
      }
      assert hasExplicitRepresentativeKey(value);
      return representatives.get(value);
    }
    return null;
  }

  @Override
  public V getRepresentativeValue(K key) {
    return get(key);
  }

  @Override
  public Set<V> getValues(K key) {
    if (containsKey(key)) {
      return Collections.singleton(get(key));
    }
    return Collections.emptySet();
  }

  @Override
  public void putAll(BidirectionalManyToOneRepresentativeMap<K, V> map) {
    map.forEachManyToOneMapping(
        (keys, value, representative) -> {
          put(keys, value);
          if (keys.size() > 1) {
            setRepresentative(value, representative);
          }
        });
  }

  @Override
  public V remove(K key) {
    V value = super.remove(key);
    if (hasExplicitRepresentativeKey(value)) {
      if (getKeys(value).size() <= 1 || getRepresentativeKey(value) == key) {
        removeRepresentativeFor(value);
      }
    }
    return value;
  }

  @Override
  public Set<K> removeValue(V value) {
    Set<K> keys = super.removeValue(value);
    removeRepresentativeFor(value);
    return keys;
  }
}
