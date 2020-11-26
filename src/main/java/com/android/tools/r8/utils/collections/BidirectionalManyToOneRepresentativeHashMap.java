// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class BidirectionalManyToOneRepresentativeHashMap<K, V>
    extends BidirectionalManyToOneHashMap<K, V>
    implements MutableBidirectionalManyToOneRepresentativeMap<K, V> {

  private final Map<V, K> representatives = new IdentityHashMap<>();

  @Override
  public void clear() {
    super.clear();
    representatives.clear();
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
  public K getRepresentativeKey(V value) {
    Set<K> keys = getKeys(value);
    if (!keys.isEmpty()) {
      return keys.size() == 1 ? keys.iterator().next() : representatives.get(value);
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
}
