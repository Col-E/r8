// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Map.Entry;
import java.util.function.BiFunction;

public class EntryUtils {

  public static <K, V, R> R accept(Entry<K, V> entry, BiFunction<K, V, R> consumer) {
    return consumer.apply(entry.getKey(), entry.getValue());
  }
}
