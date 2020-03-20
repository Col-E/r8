// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

/** A {@link TypeElement} that abstracts primitive types, which fit in 64 bits. */
public class WidePrimitiveTypeElement extends PrimitiveTypeElement {

  private static final WidePrimitiveTypeElement INSTANCE = new WidePrimitiveTypeElement();

  WidePrimitiveTypeElement() {
    super();
  }

  static WidePrimitiveTypeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isWidePrimitive() {
    return true;
  }

  @Override
  public int requiredRegisters() {
    return 2;
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
    return System.identityHashCode(INSTANCE);
  }
}
