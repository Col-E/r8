// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

/** Interface that provides mutable access to the implementation of a one-to-many mapping. */
public interface MutableBidirectionalOneToManyRepresentativeMap<K, V>
    extends MutableBidirectionalOneToManyMap<K, V>, BidirectionalOneToManyRepresentativeMap<K, V> {

  V removeRepresentativeFor(K key);

  void setRepresentative(K key, V representative);
}
