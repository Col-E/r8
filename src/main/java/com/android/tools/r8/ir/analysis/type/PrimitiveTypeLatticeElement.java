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
  TypeLatticeElement asNullable() {
    return TopTypeLatticeElement.getInstance();
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
        return IntTypeLatticeElement.getInstance();
      case 'F':
        return FloatTypeLatticeElement.getInstance();
      case 'J':
        return LongTypeLatticeElement.getInstance();
      case 'D':
        return DoubleTypeLatticeElement.getInstance();
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
        return IntTypeLatticeElement.getInstance();
      case FLOAT:
        return FloatTypeLatticeElement.getInstance();
      case LONG:
        return LongTypeLatticeElement.getInstance();
      case DOUBLE:
        return DoubleTypeLatticeElement.getInstance();
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
        return SingleTypeLatticeElement.getInstance();
      }
      assert t2.isWide();
      return TopTypeLatticeElement.getInstance();
    }
    assert t1.isWide();
    if (t2.isWide()) {
      return WideTypeLatticeElement.getInstance();
    }
    assert t2.isSingle();
    return TopTypeLatticeElement.getInstance();
  }

  public static TypeLatticeElement meet(TypeLatticeElement t1, TypeLatticeElement t2) {
    // TODO(b/72693244): !t1.isReference() && !t2.isReference();
    // TODO(b/72693244): propagate constraints backward, e.g.,
    //   vz <- add vx(1, INT) vy(0, INT_OR_FLOAT_OR_NULL)
    if (t1 == t2) {
      return t1;
    }
    if (t1.isTop()) {
      return t2;
    }
    if (t2.isTop()) {
      return t1;
    }
    if (t1.isPreciseType()) {
      if (t1.isSingle() && t2.isSingle()) {
        return t1;
      }
      if (t1.isWide() && t2.isWide()) {
        return t1;
      }
    }
    if (t2.isPreciseType()) {
      if (t2.isSingle() && t1.isSingle()) {
        return t2;
      }
      if (t2.isWide() && t1.isWide()) {
        return t2;
      }
    }
    return BottomTypeLatticeElement.getInstance();
  }

}
