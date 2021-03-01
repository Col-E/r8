// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class Int2ObjectMapUtils {

  public static <V> void forEach(Int2ObjectMap<V> map, IntObjConsumer<V> consumer) {
    for (Int2ObjectMap.Entry<V> entry : map.int2ObjectEntrySet()) {
      consumer.accept(entry.getIntKey(), entry.getValue());
    }
  }

  public static <V> V getOrDefault(Int2ObjectMap<V> map, int key, V defaultValue) {
    V value = map.get(key);
    return value != null ? value : defaultValue;
  }
}
