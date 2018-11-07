// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.NumericType;

/**
 * A {@link TypeLatticeElement} that abstracts primitive types.
 */
public abstract class PrimitiveTypeLatticeElement extends TypeLatticeElement {

  PrimitiveTypeLatticeElement() {
    super(false);
  }

  @Override
  public TypeLatticeElement asNullable() {
    return TypeLatticeElement.TOP;
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public PrimitiveTypeLatticeElement asPrimitiveTypeLatticeElement() {
    return this;
  }

  public static PrimitiveTypeLatticeElement fromDexType(DexType type) {
    assert type.isPrimitiveType();
    return fromTypeDescriptorChar((char) type.descriptor.content[0]);
  }

  public static PrimitiveTypeLatticeElement fromTypeDescriptorChar(char descriptor) {
    switch (descriptor) {
      case 'Z':
      case 'B':
      case 'S':
      case 'C':
      case 'I':
        return TypeLatticeElement.INT;
      case 'F':
        return TypeLatticeElement.FLOAT;
      case 'J':
        return TypeLatticeElement.LONG;
      case 'D':
        return TypeLatticeElement.DOUBLE;
      case 'V':
        throw new InternalCompilerError("No value type for void type.");
      default:
        throw new Unreachable("Invalid descriptor char '" + descriptor + "'");
    }
  }

  public static PrimitiveTypeLatticeElement fromNumericType(NumericType numericType) {
    switch(numericType) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return TypeLatticeElement.INT;
      case FLOAT:
        return TypeLatticeElement.FLOAT;
      case LONG:
        return TypeLatticeElement.LONG;
      case DOUBLE:
        return TypeLatticeElement.DOUBLE;
      default:
        throw new Unreachable("Invalid numeric type '" + numericType + "'");
    }
  }

  public static TypeLatticeElement join(
      PrimitiveTypeLatticeElement t1, PrimitiveTypeLatticeElement t2) {
    if (t1 == t2) {
      return t1;
    }
    if (t1.isSingle()) {
      if (t2.isSingle()) {
        return TypeLatticeElement.SINGLE;
      }
      assert t2.isWide();
      return TypeLatticeElement.TOP;
    }
    assert t1.isWide();
    if (t2.isWide()) {
      return TypeLatticeElement.WIDE;
    }
    assert t2.isSingle();
    return TypeLatticeElement.TOP;
  }
}
