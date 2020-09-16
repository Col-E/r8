// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.errors.Unreachable;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMaps;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ImmutableInt2ReferenceSortedMap<V> extends Int2ReferenceSortedMaps.EmptySortedMap<V> {

  private final Int2ReferenceSortedMap<V> sortedMap;

  private ImmutableInt2ReferenceSortedMap(Int2ReferenceSortedMap<V> sortedMap) {
    this.sortedMap = sortedMap;
  }

  public static <V> ImmutableInt2ReferenceSortedMap<V> of(Int2ReferenceSortedMap<V> sortedMap) {
    return new ImmutableInt2ReferenceSortedMap<>(sortedMap);
  }

  public static <V> ImmutableInt2ReferenceSortedMap<V> of(int[] keys, V[] values) {
    return new ImmutableInt2ReferenceSortedMap<>(new Int2ReferenceAVLTreeMap<>(keys, values));
  }

  public static <V> ImmutableInt2ReferenceSortedMap<V> empty() {
    return new ImmutableInt2ReferenceSortedMap<>(new Int2ReferenceAVLTreeMap<>());
  }

  public static <V> Builder<V> builder() {
    return new Builder<>();
  }

  public static class Builder<V> {

    private final Int2ReferenceSortedMap<V> sortedMap = new Int2ReferenceAVLTreeMap<>();

    public Builder<V> put(int k, V v) {
      sortedMap.put(k, v);
      return this;
    }

    public ImmutableInt2ReferenceSortedMap<V> build() {
      return new ImmutableInt2ReferenceSortedMap<>(sortedMap);
    }
  }

  @Override
  public V get(int k) {
    return sortedMap.get(k);
  }

  @Override
  public V get(Object ok) {
    return sortedMap.get(ok);
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return sortedMap.getOrDefault(key, defaultValue);
  }

  @Override
  public int size() {
    return sortedMap.size();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ObjectSortedSet<Entry<V>> int2ReferenceEntrySet() {
    return sortedMap.int2ReferenceEntrySet();
  }

  @Deprecated
  @Override
  @SuppressWarnings("unchecked")
  public ObjectSortedSet<Map.Entry<Integer, V>> entrySet() {
    return sortedMap.entrySet();
  }

  @Override
  public IntSortedSet keySet() {
    return sortedMap.keySet();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Int2ReferenceSortedMap<V> subMap(final int from, final int to) {
    return sortedMap.subMap(from, to);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Int2ReferenceSortedMap<V> headMap(final int to) {
    return sortedMap.headMap(to);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Int2ReferenceSortedMap<V> tailMap(final int from) {
    return sortedMap.tailMap(from);
  }

  @Override
  public boolean isEmpty() {
    return sortedMap.isEmpty();
  }

  @Override
  public int firstIntKey() {
    return sortedMap.firstIntKey();
  }

  @Override
  public int lastIntKey() {
    return sortedMap.lastIntKey();
  }

  @Deprecated
  @Override
  public Int2ReferenceSortedMap<V> headMap(Integer oto) {
    return sortedMap.headMap(oto);
  }

  @Deprecated
  @Override
  public Int2ReferenceSortedMap<V> tailMap(Integer ofrom) {
    return sortedMap.tailMap(ofrom);
  }

  @Deprecated
  @Override
  public Int2ReferenceSortedMap<V> subMap(Integer ofrom, Integer oto) {
    return sortedMap.subMap(ofrom, oto);
  }

  @Deprecated
  @Override
  public Integer firstKey() {
    return sortedMap.firstKey();
  }

  @Deprecated
  @Override
  public Integer lastKey() {
    return sortedMap.lastKey();
  }

  @Override
  public V put(int key, V value) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V put(Integer ok, V ov) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public void putAll(Map<? extends Integer, ? extends V> m) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V putIfAbsent(Integer key, V value) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V compute(
      Integer key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V computeIfAbsent(Integer key, Function<? super Integer, ? extends V> mappingFunction) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V computeIfPresent(
      Integer key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V remove(int key) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public boolean remove(Object key, Object value) {
    throw new Unreachable("Should not modify an immutable structure");
  }

  @Override
  public V remove(Object ok) {
    throw new Unreachable("Should not modify an immutable structure");
  }
}
