// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfConstNumber extends CfInstruction {

  private final long value;
  private final ValueType type;

  private static void specify(StructuralSpecification<CfConstNumber, ?> spec) {
    spec.withLong(CfConstNumber::getRawValue).withItem(CfConstNumber::getType);
  }

  public CfConstNumber(long value, ValueType type) {
    assert !type.isObject() : "Should use CfConstNull";
    this.value = value;
    this.type = type;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.CONST_NUMBER_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return visitor.visit(this, (CfConstNumber) other, CfConstNumber::specify);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, CfConstNumber::specify);
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
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    switch (type) {
      case INT:
        {
          int value = getIntValue();
          if (-1 <= value && value <= 5) {
            visitor.visitInsn(Opcodes.ICONST_0 + value);
          } else if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.BIPUSH, value);
          } else if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.SIPUSH, value);
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

  @Override
  public int bytecodeSizeUpperBound() {
    switch (type) {
      case INT:
        {
          int value = getIntValue();
          if (-1 <= value && value <= 5) {
            // iconst_0 .. iconst_5
            return 1;
          } else if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE) {
            // bipush byte
            return 2;
          } else if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE) {
            // sipush byte1 byte2
            return 3;
          } else {
            // ldc or ldc_w
            return 3;
          }
        }
      case LONG:
        {
          long value = getLongValue();
          if (value == 0 || value == 1) {
            // lconst_0 .. lconst_1
            return 1;
          } else {
            // ldc or ldc_w
            return 3;
          }
        }
      case FLOAT:
        {
          float value = getFloatValue();
          if (value == 0 || value == 1 || value == 2) {
            // fconst_0 .. fconst_2 followed by fneg if negative
            return isNegativeZeroFloat(value) ? 2 : 1;
          } else {
            // ldc or ldc_w
            return 3;
          }
        }
      case DOUBLE:
        {
          double value = getDoubleValue();
          if (value == 0 || value == 1) {
            // dconst_0 .. dconst_2 followed by dneg if negative
            return isNegativeZeroDouble(value) ? 2 : 1;
          } else {
            // ldc2_w
            return 3;
          }
        }
      default:
        throw new Unreachable("Non supported type in cf backend: " + type);
    }
  }

  public static boolean isNegativeZeroDouble(double value) {
    return Double.doubleToLongBits(value) == Double.doubleToLongBits(-0.0);
  }

  public static boolean isNegativeZeroFloat(float value) {
    return Float.floatToIntBits(value) == Float.floatToIntBits(-0.0f);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addConst(type.toPrimitiveType(), state.push(type).register, value);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forConstInstruction();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ... →
    // ..., value
    assert type.isPrimitive();
    return frame.push(appView, config, type);
  }
}
