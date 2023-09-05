// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberConversion;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;

public class StringBuilderHelper {

  static boolean isEscapingInstructionForInValues(Instruction instruction) {
    return instruction.isFieldPut()
        || instruction.isInvoke()
        || instruction.isReturn()
        || instruction.isArrayPut();
  }

  static boolean isEscapingInstructionForOutValues(Instruction instruction) {
    return instruction.isArgument()
        || instruction.isInvoke()
        || instruction.isFieldGet()
        || instruction.isArrayGet()
        || instruction.isCheckCast();
  }

  static boolean canMutate(Instruction instruction) {
    return instruction.isInvoke()
        || instruction.isFieldInstruction()
        || instruction.isNewInstance();
  }

  static boolean isInstructionThatIntroducesDefiniteAlias(
      Instruction instruction, StringBuilderOracle oracle) {
    return instruction.isAssume() || instruction.isCheckCast() || oracle.isAppend(instruction);
  }

  @SuppressWarnings("ReferenceEquality")
  static String extractConstantArgument(
      DexItemFactory factory, DexMethod method, Value arg, DexType argumentType) {
    if (arg.isPhi()) {
      return null;
    }
    if (arg.isConstString()) {
      return arg.definition.asConstString().getValue().toString();
    }
    Number constantNumber = extractConstantNumber(factory, arg);
    if (constantNumber == null) {
      return null;
    }
    if (arg.getType().isPrimitiveType()) {
      if (argumentType == factory.booleanType) {
        return String.valueOf(constantNumber.intValue() != 0);
      } else if (argumentType == factory.byteType) {
        return String.valueOf(constantNumber.byteValue());
      } else if (argumentType == factory.shortType) {
        return String.valueOf(constantNumber.shortValue());
      } else if (argumentType == factory.charType) {
        return String.valueOf((char) constantNumber.intValue());
      } else if (argumentType == factory.intType) {
        return String.valueOf(constantNumber.intValue());
      } else if (argumentType == factory.longType) {
        return String.valueOf(constantNumber.longValue());
      } else if (argumentType == factory.floatType) {
        return String.valueOf(constantNumber.floatValue());
      } else if (argumentType == factory.doubleType) {
        return String.valueOf(constantNumber.doubleValue());
      }
    } else if (arg.getType().isNullType()
        && !method.isInstanceInitializer(factory)
        && argumentType != factory.charArrayType) {
      assert constantNumber.intValue() == 0;
      return "null";
    }
    return null;
  }

  @SuppressWarnings("ReferenceEquality")
  static Number extractConstantNumber(DexItemFactory factory, Value arg) {
    if (arg.isPhi()) {
      return null;
    }
    if (arg.definition.isConstNumber()) {
      ConstNumber cst = arg.definition.asConstNumber();
      if (cst.outType() == ValueType.LONG) {
        return cst.getLongValue();
      } else if (cst.outType() == ValueType.FLOAT) {
        return cst.getFloatValue();
      } else if (cst.outType() == ValueType.DOUBLE) {
        return cst.getDoubleValue();
      } else {
        assert cst.outType() == ValueType.INT || cst.outType() == ValueType.OBJECT;
        return cst.getIntValue();
      }
    } else if (arg.definition.isNumberConversion()) {
      NumberConversion conversion = arg.definition.asNumberConversion();
      assert conversion.inValues().size() == 1;
      Number temp = extractConstantNumber(factory, conversion.inValues().get(0));
      if (temp == null) {
        return null;
      }
      DexType conversionType = conversion.to.toDexType(factory);
      if (conversionType == factory.booleanType) {
        return temp.intValue() != 0 ? 1 : 0;
      } else if (conversionType == factory.byteType) {
        return temp.byteValue();
      } else if (conversionType == factory.shortType) {
        return temp.shortValue();
      } else if (conversionType == factory.charType) {
        return temp.intValue();
      } else if (conversionType == factory.intType) {
        return temp.intValue();
      } else if (conversionType == factory.longType) {
        return temp.longValue();
      } else if (conversionType == factory.floatType) {
        return temp.floatValue();
      } else if (conversionType == factory.doubleType) {
        return temp.doubleValue();
      }
    }
    return null;
  }
}
