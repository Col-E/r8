// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

/**
 * Interface that accommodates one-to-one mappings.
 *
 * <p>This interface inherits from {@link BidirectionalManyToManyRepresentativeMap} to allow
 * implementing many-to-many mappings using one-to-one mappings.
 */
public interface MutableBidirectionalOneToOneMap<K, V> extends BidirectionalOneToOneMap<K, V> {

  V put(K key, V value);

  void putAll(BidirectionalManyToManyMap<K, V> map);
}
