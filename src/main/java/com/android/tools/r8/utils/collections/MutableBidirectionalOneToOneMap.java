// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

/** Interface that provides mutable access to the implementation of a one-to-one mapping. */
public interface MutableBidirectionalOneToOneMap<K, V> extends BidirectionalOneToOneMap<K, V> {

  V put(K key, V value);

  void putAll(BidirectionalManyToManyMap<K, V> map);
}
