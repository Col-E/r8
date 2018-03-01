// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.ValueType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfConstNumber extends CfInstruction {

  private final long value;
  private final ValueType type;

  public CfConstNumber(long value, ValueType type) {
    this.value = value;
    this.type = type;
  }

  public ValueType getType() {
    return type;
  }

  public long getRawValue() {
    return value;
  }

  public int getIntValue() {
    assert type == ValueType.INT;
    return (int) value;
  }

  public long getLongValue() {
    assert type == ValueType.LONG;
    return value;
  }

  public float getFloatValue() {
    assert type == ValueType.FLOAT;
    return Float.intBitsToFloat((int) value);
  }

  public double getDoubleValue() {
    assert type == ValueType.DOUBLE;
    return Double.longBitsToDouble(value);
  }

  @Override
  public void write(MethodVisitor visitor) {
    switch (type) {
      case INT:
        {
          int value = getIntValue();
          if (-1 <= value && value <= 5) {
            visitor.visitInsn(Opcodes.ICONST_0 + value);
          } else {
            visitor.visitLdcInsn(value);
          }
          break;
        }
      case LONG:
        {
          long value = getLongValue();
          if (value == 0 || value == 1) {
            visitor.visitInsn(Opcodes.LCONST_0 + (int) value);
          } else {
            visitor.visitLdcInsn(value);
          }
          break;
        }
      case FLOAT:
        {
          float value = getFloatValue();
          if (value == 0 || value == 1 || value == 2) {
            visitor.visitInsn(Opcodes.FCONST_0 + (int) value);
            if (isNegativeZeroFloat(value)) {
              visitor.visitInsn(Opcodes.FNEG);
            }
          } else {
            visitor.visitLdcInsn(value);
          }
          break;
        }
      case DOUBLE:
        {
          double value = getDoubleValue();
          if (value == 0 || value == 1) {
            visitor.visitInsn(Opcodes.DCONST_0 + (int) value);
            if (isNegativeZeroDouble(value)) {
              visitor.visitInsn(Opcodes.DNEG);
            }
          } else {
            visitor.visitLdcInsn(value);
          }
          break;
        }
      default:
        throw new Unreachable("Non supported type in cf backend: " + type);
    }
  }

  private static boolean isNegativeZeroDouble(double value) {
    return Double.doubleToLongBits(value) == Double.doubleToLongBits(-0.0);
  }

  private static boolean isNegativeZeroFloat(float value) {
    return Float.floatToIntBits(value) == Float.floatToIntBits(-0.0f);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
