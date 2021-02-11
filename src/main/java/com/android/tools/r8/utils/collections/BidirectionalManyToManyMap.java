// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Interface that accommodates many-to-many mappings. */
public interface BidirectionalManyToManyMap<K, V> {

  boolean containsKey(K key);

  boolean containsValue(V value);

  void forEach(BiConsumer<? super K, ? super V> consumer);

  void forEachKey(Consumer<? super K> consumer);

  void forEachValue(Consumer<? super V> consumer);

  Set<K> getKeys(V value);

  Set<V> getValues(K key);

  boolean isEmpty();
}
