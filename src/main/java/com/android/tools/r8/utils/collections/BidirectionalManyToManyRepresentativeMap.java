// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

/**
 * Interface that accommodates many-to-many mappings.
 *
 * <p>This interface additionally adds a "representative" for each one-to-many/many-to-one mapping.
 * The representative for a given key is a value from {@link #getValues(K)}. The representative for
 * a given value is a key from {@link #getKeys(V)}.
 */
public interface BidirectionalManyToManyRepresentativeMap<K, V>
    extends BidirectionalManyToManyMap<K, V> {

  boolean hasExplicitRepresentativeKey(V value);

  K getRepresentativeKey(V value);

  default K getRepresentativeKeyOrDefault(V value, K defaultValue) {
    K representativeKey = getRepresentativeKey(value);
    return representativeKey != null ? representativeKey : defaultValue;
  }

  V getRepresentativeValue(K key);

  default V getRepresentativeValueOrDefault(K key, V defaultValue) {
    V representativeValue = getRepresentativeValue(key);
    return representativeValue != null ? representativeValue : defaultValue;
  }
}
