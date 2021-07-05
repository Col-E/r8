// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Interface that accommodates many-to-many mappings.
 *
 * <p>This interface inherits from {@link BidirectionalManyToManyMap} to allow implementing
 * many-to-many mappings using many-to-one mappings.
 */
public interface BidirectionalOneToManyMap<K, V> extends BidirectionalManyToManyMap<K, V> {

  void forEachOneToManyMapping(BiConsumer<K, Set<V>> consumer);

  Set<V> get(Object key);

  Set<V> getOrDefault(Object key, Set<V> defaultValue);

  K getKey(V value);

  K getKeyOrDefault(V value, K defaultValue);
}
