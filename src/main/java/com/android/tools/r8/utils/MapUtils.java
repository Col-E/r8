// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class MapUtils {

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

  public static <T> void removeIdentityMappings(Map<T, T> map) {
    map.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
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
      BiFunction<V2, V2, V2> valueMerger) {
    Map<K2, V2> result = factory.apply(map.size());
    map.forEach(
        (key, value) -> {
          K2 newKey = keyMapping.apply(key);
          if (newKey == null) {
            return;
          }
          V2 newValue = valueMapping.apply(value);
          V2 existingValue = result.put(newKey, newValue);
          if (existingValue != null) {
            result.put(newKey, valueMerger.apply(existingValue, newValue));
          }
        });
    return result;
  }
}
