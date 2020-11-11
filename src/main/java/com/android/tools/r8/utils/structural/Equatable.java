// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

public interface Equatable<T> {

  /**
   * Typed definition of equality.
   *
   * <p>Subclasses must implement this and override Object.equals(Object) with equalsImpl.
   */
  boolean isEqualTo(T other);

  /**
   * An equatable type must define an equality compatible hashing.
   *
   * <p>Note: that that the declaration here will not enforce an implementation in the concrete
   * class and it cannot be defined by a default method. Implementors of Equatable must ensure to
   * override it.
   */
  @Override
  int hashCode();

  /**
   * An equatable type must override Object.equals by the intended implementation below.
   *
   * <p>Note: that that the declaration here will not enforce an implementation in the concrete
   * class and it cannot be defined by a default method. Implementors of Equatable must ensure to
   * override it.
   */
  @Override
  boolean equals(Object other);

  /**
   * Implementation for Object.equals(Object).
   *
   * <p>It is not possible to define default methods on java.lang.Object, thus concrete subclasses
   * must manually override equals as:
   *
   * <pre>
   *   @Override boolean equals(Object other) { return Equatable.equalsImpl(this, other); }
   * </pre>
   */
  @SuppressWarnings("unchecked")
  static <T extends Equatable<T>> boolean equalsImpl(T self, Object other) {
    assert self != null;
    if (self == other) {
      return true;
    }
    if (other == null || self.getClass() != other.getClass()) {
      return false;
    }
    return self.isEqualTo((T) other);
  }
}
