// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

/**
 * A {@link TypeLatticeElement} that abstracts primitive types, which fit in 64 bits.
 */
public class WideTypeLatticeElement extends PrimitiveTypeLatticeElement {
  private static final WideTypeLatticeElement WIDE_INSTANCE = new WideTypeLatticeElement();

  WideTypeLatticeElement() {
    super();
  }

  static WideTypeLatticeElement getInstance() {
    return WIDE_INSTANCE;
  }

  @Override
  public boolean isWide() {
    return true;
  }

  @Override
  public String toString() {
    return "WIDE";
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(WIDE_INSTANCE);
  }
}
