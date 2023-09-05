// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.NumericType;

/** A {@link TypeElement} that abstracts primitive types. */
public abstract class PrimitiveTypeElement extends TypeElement {

  public abstract String getDescriptor();

  public abstract String getTypeName();

  @Override
  public Nullability nullability() {
    return Nullability.definitelyNotNull();
  }

  @Override
  public boolean isPrimitiveType() {
    return true;
  }

  @Override
  public PrimitiveTypeElement asPrimitiveType() {
    return this;
  }

  static PrimitiveTypeElement fromDexType(DexType type, boolean asArrayElementType) {
    assert type.isPrimitiveType();
    return fromTypeDescriptorChar((char) type.descriptor.content[0], asArrayElementType);
  }

  public DexType toDexType(DexItemFactory factory) {
    if (isBoolean()) {
      return factory.booleanType;
    }
    if (isByte()) {
      return factory.byteType;
    }
    if (isShort()) {
      return factory.shortType;
    }
    if (isChar()) {
      return factory.charType;
    }
    if (isInt()) {
      return factory.intType;
    }
    if (isFloat()) {
      return factory.floatType;
    }
    if (isLong()) {
      return factory.longType;
    }
    if (isDouble()) {
      return factory.doubleType;
    }
    throw new Unreachable("Imprecise primitive type '" + toString() + "'");
  }

  public boolean hasDexType() {
    return isBoolean()
        || isByte()
        || isShort()
        || isChar()
        || isInt()
        || isFloat()
        || isLong()
        || isDouble();
  }

  private static PrimitiveTypeElement fromTypeDescriptorChar(
      char descriptor, boolean asArrayElementType) {
    switch (descriptor) {
      case 'Z':
        if (asArrayElementType) {
          return TypeElement.getBoolean();
        }
        // fall through
      case 'B':
        if (asArrayElementType) {
          return TypeElement.getByte();
        }
        // fall through
      case 'S':
        if (asArrayElementType) {
          return TypeElement.getShort();
        }
        // fall through
      case 'C':
        if (asArrayElementType) {
          return TypeElement.getChar();
        }
        // fall through
      case 'I':
        return TypeElement.getInt();
      case 'F':
        return TypeElement.getFloat();
      case 'J':
        return TypeElement.getLong();
      case 'D':
        return TypeElement.getDouble();
      case 'V':
        throw new InternalCompilerError("No value type for void type.");
      default:
        throw new Unreachable("Invalid descriptor char '" + descriptor + "'");
    }
  }

  public static PrimitiveTypeElement fromNumericType(NumericType numericType) {
    switch(numericType) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return TypeElement.getInt();
      case FLOAT:
        return TypeElement.getFloat();
      case LONG:
        return TypeElement.getLong();
      case DOUBLE:
        return TypeElement.getDouble();
      default:
        throw new Unreachable("Invalid numeric type '" + numericType + "'");
    }
  }

  @SuppressWarnings("ReferenceEquality")
  TypeElement join(PrimitiveTypeElement other) {
    if (this == other) {
      return this;
    }
    if (isSinglePrimitive()) {
      if (other.isSinglePrimitive()) {
        return TypeElement.getSingle();
      }
      assert other.isWidePrimitive();
      return TypeElement.getTop();
    }
    assert isWidePrimitive();
    if (other.isWidePrimitive()) {
      return TypeElement.getWide();
    }
    assert other.isSinglePrimitive();
    return TypeElement.getTop();
  }
}
