// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public enum ValueType {
  OBJECT,
  INT,
  FLOAT,
  LONG,
  DOUBLE;

  public boolean isObject() {
    return this == OBJECT;
  }

  public boolean isSingle() {
    return this == INT || this == FLOAT;
  }

  public boolean isWide() {
    return this == LONG || this == DOUBLE;
  }

  public int requiredRegisters() {
    return isWide() ? 2 : 1;
  }

  public static ValueType fromMemberType(MemberType type) {
    switch (type) {
      case BOOLEAN_OR_BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return ValueType.INT;
      case FLOAT:
        return ValueType.FLOAT;
      case LONG:
        return ValueType.LONG;
      case DOUBLE:
        return ValueType.DOUBLE;
      case OBJECT:
        return ValueType.OBJECT;
      case INT_OR_FLOAT:
      case LONG_OR_DOUBLE:
        throw new Unreachable("Unexpected imprecise type: " + type);
      default:
        throw new Unreachable("Unexpected member type: " + type);
    }
  }

  public static ValueType fromTypeDescriptorChar(char descriptor) {
    switch (descriptor) {
      case 'L':
      case '[':
        return ValueType.OBJECT;
      case 'Z':
      case 'B':
      case 'S':
      case 'C':
      case 'I':
        return ValueType.INT;
      case 'F':
        return ValueType.FLOAT;
      case 'J':
        return ValueType.LONG;
      case 'D':
        return ValueType.DOUBLE;
      case 'V':
        throw new InternalCompilerError("No value type for void type.");
      default:
        throw new Unreachable("Invalid descriptor char '" + descriptor + "'");
    }
  }

  public static ValueType fromDexType(DexType type) {
    return fromTypeDescriptorChar((char) type.descriptor.content[0]);
  }

  public static ValueType fromNumericType(NumericType type) {
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return ValueType.INT;
      case FLOAT:
        return ValueType.FLOAT;
      case LONG:
        return ValueType.LONG;
      case DOUBLE:
        return ValueType.DOUBLE;
      default:
        throw new Unreachable("Invalid numeric type '" + type + "'");
    }
  }

  public static ValueType fromType(TypeElement type) {
    if (type.isReferenceType()) {
      return OBJECT;
    }
    if (type.isInt()) {
      return INT;
    }
    if (type.isFloat()) {
      return FLOAT;
    }
    if (type.isLong()) {
      return LONG;
    }
    if (type.isDouble()) {
      return DOUBLE;
    }
    throw new Unreachable("Unexpected conversion of imprecise type: " + type);
  }

  public PrimitiveTypeElement toPrimitiveType() {
    switch (this) {
      case INT:
        return TypeElement.getInt();
      case FLOAT:
        return TypeElement.getFloat();
      case LONG:
        return TypeElement.getLong();
      case DOUBLE:
        return TypeElement.getDouble();
      default:
        throw new Unreachable("Unexpected type in conversion to primitive: " + this);
    }
  }
}
