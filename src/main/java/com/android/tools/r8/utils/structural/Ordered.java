// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

/** An ordered type is a type with a total order. */
public interface Ordered<T extends Ordered<T>> extends Equatable<T>, Comparable<T> {

  /** Definition of total order. */
  @Override
  int compareTo(T other);

  /** Default equality is now defined by the order. */
  @Override
  default boolean isEqualTo(T other) {
    assert other != null;
    return this == other || compareTo(other) == 0;
  }

  static <T extends Ordered<T>> T min(T o1, T o2) {
    return o1.isLessThan(o2) ? o1 : o2;
  }

  static <T extends Ordered<T>> T max(T o1, T o2) {
    return o1.isLessThan(o2) ? o2 : o1;
  }

  static <T extends Ordered<T>> T minIgnoreNull(T o1, T o2) {
    if (o1 == null) {
      return o2;
    }
    if (o2 == null) {
      return o1;
    }
    return min(o1, o2);
  }

  static <T extends Ordered<T>> T maxIgnoreNull(T o1, T o2) {
    if (o1 == null) {
      return o2;
    }
    if (o2 == null) {
      return o1;
    }
    return o1.isLessThan(o2) ? o2 : o1;
  }

  default boolean isLessThan(T other) {
    return compareTo(other) < 0;
  }

  default boolean isLessThanOrEqualTo(T other) {
    return compareTo(other) <= 0;
  }

  default boolean isGreaterThan(T other) {
    return compareTo(other) > 0;
  }

  default boolean isGreaterThanOrEqualTo(T other) {
    return compareTo(other) >= 0;
  }

  default boolean betweenBothIncluded(T lower, T upper) {
    return isGreaterThanOrEqualTo(lower) && isLessThanOrEqualTo(upper);
  }
}
