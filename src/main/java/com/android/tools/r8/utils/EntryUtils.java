// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EntryUtils {

  public static <K, V> Consumer<Entry<K, V>> accept(BiConsumer<K, V> consumer) {
    return entry -> consumer.accept(entry.getKey(), entry.getValue());
  }
}
