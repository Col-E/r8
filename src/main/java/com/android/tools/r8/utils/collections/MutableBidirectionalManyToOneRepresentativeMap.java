// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

/** Interface that provides mutable access to the implementation of a many-to-one mapping. */
public interface MutableBidirectionalManyToOneRepresentativeMap<K, V>
    extends MutableBidirectionalManyToOneMap<K, V>, BidirectionalManyToOneRepresentativeMap<K, V> {

  void putAll(BidirectionalManyToOneRepresentativeMap<K, V> map);

  K removeRepresentativeFor(V value);

  void setRepresentative(V value, K representative);
}
