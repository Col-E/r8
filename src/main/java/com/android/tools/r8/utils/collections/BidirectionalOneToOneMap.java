// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.google.common.collect.BiMap;

/**
 * Interface that accommodates one-to-one mappings.
 *
 * <p>This interface inherits from {@link BidirectionalManyToManyRepresentativeMap} to allow
 * implementing many-to-many mappings using one-to-one mappings.
 */
public interface BidirectionalOneToOneMap<K, V>
    extends BidirectionalManyToOneRepresentativeMap<K, V> {

  @Override
  BiMap<K, V> getForwardMap();

  BidirectionalOneToOneMap<V, K> getInverseOneToOneMap();
}
