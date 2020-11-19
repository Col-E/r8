// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Map;

public abstract class BidirectionalManyToManyRepresentativeMap<K, V> {

  public static <K, V> BidirectionalManyToManyRepresentativeMap<K, V> empty() {
    return new EmptyBidirectionalManyToManyRepresentativeMap<>();
  }

  public abstract boolean containsKey(K key);

  public abstract boolean containsValue(V value);

  public abstract Map<K, V> getForwardBacking();

  public abstract Map<V, K> getInverseBacking();

  public final Inverse getInverseManyToManyMap() {
    return new Inverse();
  }

  public abstract K getRepresentativeKey(V value);

  public final K getRepresentativeKeyOrDefault(V value, K defaultValue) {
    K representativeKey = getRepresentativeKey(value);
    return representativeKey != null ? representativeKey : defaultValue;
  }

  public abstract V getRepresentativeValue(K key);

  public final V getRepresentativeValueOrDefault(K key, V defaultValue) {
    V representativeValue = getRepresentativeValue(key);
    return representativeValue != null ? representativeValue : defaultValue;
  }

  public abstract Iterable<K> getKeys(V value);

  public abstract Iterable<V> getValues(K key);

  public abstract boolean isEmpty();

  public class Inverse extends BidirectionalManyToManyRepresentativeMap<V, K> {

    @Override
    public boolean containsKey(V key) {
      return BidirectionalManyToManyRepresentativeMap.this.containsValue(key);
    }

    @Override
    public boolean containsValue(K value) {
      return BidirectionalManyToManyRepresentativeMap.this.containsKey(value);
    }

    @Override
    public Map<V, K> getForwardBacking() {
      return BidirectionalManyToManyRepresentativeMap.this.getInverseBacking();
    }

    @Override
    public Map<K, V> getInverseBacking() {
      return BidirectionalManyToManyRepresentativeMap.this.getForwardBacking();
    }

    @Override
    public V getRepresentativeKey(K value) {
      return BidirectionalManyToManyRepresentativeMap.this.getRepresentativeValue(value);
    }

    @Override
    public K getRepresentativeValue(V key) {
      return BidirectionalManyToManyRepresentativeMap.this.getRepresentativeKey(key);
    }

    @Override
    public Iterable<V> getKeys(K value) {
      return BidirectionalManyToManyRepresentativeMap.this.getValues(value);
    }

    @Override
    public Iterable<K> getValues(V key) {
      return BidirectionalManyToManyRepresentativeMap.this.getKeys(key);
    }

    @Override
    public boolean isEmpty() {
      return BidirectionalManyToManyRepresentativeMap.this.isEmpty();
    }
  }
}
