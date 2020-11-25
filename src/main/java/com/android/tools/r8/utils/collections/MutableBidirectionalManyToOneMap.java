// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import java.util.Set;

/**
 * Interface that accommodates many-to-one mappings.
 *
 * <p>This interface inherits from {@link BidirectionalManyToManyMap} to allow implementing
 * many-to-many mappings using many-to-one mappings.
 */
public interface MutableBidirectionalManyToOneMap<K, V> extends BidirectionalManyToOneMap<K, V> {

  void put(K key, V value);

  V remove(K key);

  Set<K> removeValue(V value);
}
