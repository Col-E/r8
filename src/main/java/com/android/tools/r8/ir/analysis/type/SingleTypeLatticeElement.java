// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

/**
 * A {@link TypeLatticeElement} that abstracts primitive types, which fit in 32 bits.
 */
public class SingleTypeLatticeElement extends PrimitiveTypeLatticeElement {
  private static final SingleTypeLatticeElement SINGLE_INSTANCE = new SingleTypeLatticeElement();

  SingleTypeLatticeElement() {
    super();
  }

  static SingleTypeLatticeElement getInstance() {
    return SINGLE_INSTANCE;
  }

  @Override
  public boolean isSingle() {
    return true;
  }

  @Override
  public String toString() {
    return "SINGLE";
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
