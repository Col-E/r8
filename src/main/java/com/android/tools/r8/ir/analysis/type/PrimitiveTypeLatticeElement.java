// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

/**
 * A {@link TypeLatticeElement} that represents all primitive types, such as int, long, etc.
 * All primitives are aggregated, since the main use of this type analysis is to help
 * better understanding of the type of object references.
 */
public class PrimitiveTypeLatticeElement extends TypeLatticeElement {
  private static final PrimitiveTypeLatticeElement INSTANCE = new PrimitiveTypeLatticeElement();

  private PrimitiveTypeLatticeElement() {
    super(false);
  }

  @Override
  TypeLatticeElement asNullable() {
    return Top.getInstance();
  }

  public static PrimitiveTypeLatticeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public String toString() {
    return "PRIMITIVE";
  }
}
