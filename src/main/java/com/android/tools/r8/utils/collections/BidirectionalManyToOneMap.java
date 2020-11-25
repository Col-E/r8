// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Interface that accommodates many-to-one mappings.
 *
 * <p>This interface inherits from {@link BidirectionalManyToManyMap} to allow implementing
 * many-to-many mappings using many-to-one mappings.
 */
public interface BidirectionalManyToOneMap<K, V> extends BidirectionalManyToManyMap<K, V> {

  void forEachManyToOneMapping(BiConsumer<? super Set<K>, V> consumer);

  V get(Object key);

  V getOrDefault(Object key, V defaultValue);

  Map<K, V> getForwardMap();

  Set<K> keySet();

  Set<V> values();
}
