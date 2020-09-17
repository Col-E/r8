// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

public class MapUtils {

  public static <K, V> Map<K, V> map(
      Map<K, V> map,
      IntFunction<Map<K, V>> factory,
      Function<K, K> keyMapping,
      Function<V, V> valueMapping,
      BiFunction<V, V, V> valueMerger) {
    Map<K, V> result = factory.apply(map.size());
    map.forEach(
        (key, value) -> {
          K newKey = keyMapping.apply(key);
          V newValue = valueMapping.apply(value);
          V existingValue = result.put(newKey, newValue);
          if (existingValue != null) {
            result.put(newKey, valueMerger.apply(existingValue, newValue));
          }
        });
    return result;
  }

  public static <T> void removeIdentityMappings(Map<T, T> map) {
    map.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
  }

  public static String toString(Map<?, ?> map) {
    return StringUtils.join(
        map.entrySet(), ",", BraceType.TUBORG, entry -> entry.getKey() + ":" + entry.getValue());
  }
}
