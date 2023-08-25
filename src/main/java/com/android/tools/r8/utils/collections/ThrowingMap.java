// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.errors.Unreachable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ThrowingMap<K, V> implements Map<K, V> {

  private static final ThrowingMap<?, ?> INSTANCE = new ThrowingMap<>();

  private ThrowingMap() {}

  @SuppressWarnings("unchecked")
  public static <K, V> ThrowingMap<K, V> get() {
    return (ThrowingMap<K, V>) INSTANCE;
  }

  public static boolean isThrowingMap(Map<?, ?> map) {
    return map == get();
  }

  @Override
  public void clear() {
    throw new Unreachable();
  }

  @Override
  public boolean containsKey(Object key) {
    throw new Unreachable();
  }

  @Override
  public boolean containsValue(Object value) {
    throw new Unreachable();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    throw new Unreachable();
  }

  @Override
  public V get(Object key) {
    throw new Unreachable();
  }

  @Override
  public boolean isEmpty() {
    throw new Unreachable();
  }

  @Override
  public Set<K> keySet() {
    throw new Unreachable();
  }

  @Override
  public V put(K key, V value) {
    throw new Unreachable();
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    throw new Unreachable();
  }

  @Override
  public V remove(Object key) {
    throw new Unreachable();
  }

  @Override
  public int size() {
    throw new Unreachable();
  }

  @Override
  public Collection<V> values() {
    throw new Unreachable();
  }
}
