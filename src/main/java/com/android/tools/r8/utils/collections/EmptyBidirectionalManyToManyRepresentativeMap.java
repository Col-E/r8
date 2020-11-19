// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.utils.IterableUtils;
import java.util.Collections;
import java.util.Map;

public class EmptyBidirectionalManyToManyRepresentativeMap<K, V>
    extends BidirectionalManyToManyRepresentativeMap<K, V> {

  @Override
  public boolean containsKey(K key) {
    return false;
  }

  @Override
  public boolean containsValue(V value) {
    return false;
  }

  @Override
  public Map<K, V> getForwardBacking() {
    return Collections.emptyMap();
  }

  @Override
  public Map<V, K> getInverseBacking() {
    return Collections.emptyMap();
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
  public Iterable<K> getKeys(V value) {
    return IterableUtils.empty();
  }

  @Override
  public Iterable<V> getValues(K key) {
    return IterableUtils.empty();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }
}
