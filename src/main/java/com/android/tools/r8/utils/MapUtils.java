// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.StringUtils.BraceType;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class MapUtils {

  public static <V> Int2ReferenceMap<V> canonicalizeEmptyMap(Int2ReferenceMap<V> map) {
    return map.isEmpty() ? Int2ReferenceMaps.emptyMap() : map;
  }

  public static <K, V> Map<K, V> clone(
      Map<K, V> mapToClone, Map<K, V> newMap, Function<V, V> valueCloner) {
    mapToClone.forEach((key, value) -> newMap.put(key, valueCloner.apply(value)));
    return newMap;
  }

  public static <K, V> K firstKey(Map<K, V> map) {
    return map.keySet().iterator().next();
  }

  public static <K, V> V firstValue(Map<K, V> map) {
    return map.values().iterator().next();
  }

  public static <T, R> Function<T, R> ignoreKey(Supplier<R> supplier) {
    return ignore -> supplier.get();
  }

  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap(BiForEachable<K, V> forEachable) {
    IdentityHashMap<K, V> map = new IdentityHashMap<>();
    forEachable.forEach(map::put);
    return map;
  }

  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap(
      BiForEachable<K, V> forEachable, int capacity) {
    IdentityHashMap<K, V> map = new IdentityHashMap<>(capacity);
    forEachable.forEach(map::put);
    return map;
  }

  public static <T> void removeIdentityMappings(Map<T, T> map) {
    map.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
  }

  public static <K, V> void removeIf(Map<K, V> map, BiPredicate<K, V> predicate) {
    map.entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
  }

  public static <K, V> V removeOrDefault(Map<K, V> map, K key, V defaultValue) {
    V value = map.remove(key);
    return value != null ? value : defaultValue;
  }

  public static String toString(Map<?, ?> map) {
    return StringUtils.join(
        ",", map.entrySet(), entry -> entry.getKey() + ":" + entry.getValue(), BraceType.TUBORG);
  }

  public static <K1, V1, K2, V2> Map<K2, V2> transform(
      Map<K1, V1> map,
      IntFunction<Map<K2, V2>> factory,
      Function<K1, K2> keyMapping,
      Function<V1, V2> valueMapping,
      TriFunction<K2, V2, V2, V2> valueMerger) {
    return transform(
        map,
        factory,
        (key, value) -> keyMapping.apply(key),
        (key, value) -> valueMapping.apply(value),
        valueMerger);
  }

  public static <K1, V1, K2, V2> Map<K2, V2> transform(
      Map<K1, V1> map,
      IntFunction<Map<K2, V2>> factory,
      BiFunction<K1, V1, K2> keyMapping,
      BiFunction<K1, V1, V2> valueMapping,
      TriFunction<K2, V2, V2, V2> valueMerger) {
    Map<K2, V2> result = factory.apply(map.size());
    map.forEach(
        (key, value) -> {
          K2 newKey = keyMapping.apply(key, value);
          if (newKey == null) {
            return;
          }
          V2 newValue = valueMapping.apply(key, value);
          V2 existingValue = result.put(newKey, newValue);
          if (existingValue != null) {
            result.put(newKey, valueMerger.apply(newKey, existingValue, newValue));
          }
        });
    return result;
  }

  public static <K, V> boolean equals(Map<K, V> one, Map<K, V> other) {
    if (one == other) {
      return true;
    }
    if (one.size() != other.size()) {
      return false;
    }
    for (Entry<K, V> firstEntry : one.entrySet()) {
      if (!firstEntry.getValue().equals(other.get(firstEntry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  public static <K, V> Map<K, V> unmodifiableForTesting(Map<K, V> map) {
    return InternalOptions.assertionsEnabled() ? Collections.unmodifiableMap(map) : map;
  }
}
