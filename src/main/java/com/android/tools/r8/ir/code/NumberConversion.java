// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfNumberConversion;
import com.android.tools.r8.dex.code.DexDoubleToFloat;
import com.android.tools.r8.dex.code.DexDoubleToInt;
import com.android.tools.r8.dex.code.DexDoubleToLong;
import com.android.tools.r8.dex.code.DexFloatToDouble;
import com.android.tools.r8.dex.code.DexFloatToInt;
import com.android.tools.r8.dex.code.DexFloatToLong;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexIntToByte;
import com.android.tools.r8.dex.code.DexIntToChar;
import com.android.tools.r8.dex.code.DexIntToDouble;
import com.android.tools.r8.dex.code.DexIntToFloat;
import com.android.tools.r8.dex.code.DexIntToLong;
import com.android.tools.r8.dex.code.DexIntToShort;
import com.android.tools.r8.dex.code.DexLongToDouble;
import com.android.tools.r8.dex.code.DexLongToFloat;
import com.android.tools.r8.dex.code.DexLongToInt;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Set;

public class NumberConversion extends Unop {

  public final NumericType from;
  public final NumericType to;

  public NumberConversion(NumericType from, NumericType to, Value dest, Value source) {
    super(dest, source);
    this.from = from;
    this.to = to;
  }

  @Override
  public int opcode() {
    return Opcodes.NUMBER_CONVERSION;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public boolean isLongToIntConversion() {
    return from == NumericType.LONG && to == NumericType.INT;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    int src = builder.allocatedRegister(source(), getNumber());
    switch (from) {
      case INT:
        switch (to) {
          case BYTE:
            instruction = new DexIntToByte(dest, src);
            break;
          case CHAR:
            instruction = new DexIntToChar(dest, src);
            break;
          case SHORT:
            instruction = new DexIntToShort(dest, src);
            break;
          case LONG:
            instruction = new DexIntToLong(dest, src);
            break;
          case FLOAT:
            instruction = new DexIntToFloat(dest, src);
            break;
          case DOUBLE:
            instruction = new DexIntToDouble(dest, src);
            break;
          default:
            throw new Unreachable("Unexpected types " + from + ", " + to);
        }
        break;
      case LONG:
        switch (to) {
          case INT:
            instruction = new DexLongToInt(dest, src);
            break;
          case FLOAT:
            instruction = new DexLongToFloat(dest, src);
            break;
          case DOUBLE:
            instruction = new DexLongToDouble(dest, src);
            break;
          default:
            throw new Unreachable("Unexpected types " + from + ", " + to);
        }
        break;
      case FLOAT:
        switch (to) {
          case INT:
            instruction = new DexFloatToInt(dest, src);
            break;
          case LONG:
            instruction = new DexFloatToLong(dest, src);
            break;
          case DOUBLE:
            instruction = new DexFloatToDouble(dest, src);
            break;
          default:
            throw new Unreachable("Unexpected types " + from + ", " + to);
        }
        break;
      case DOUBLE:
        switch (to) {
          case INT:
            instruction = new DexDoubleToInt(dest, src);
            break;
          case LONG:
            instruction = new DexDoubleToLong(dest, src);
            break;
          case FLOAT:
            instruction = new DexDoubleToFloat(dest, src);
            break;
          default:
            throw new Unreachable("Unexpected types " + from + ", " + to);
        }
        break;
      default:
        throw new Unreachable("Unexpected types " + from + ", " + to);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isNumberConversion()) {
      return false;
    }
    NumberConversion o = other.asNumberConversion();
    return o.from == from && o.to == to;
  }

  @Override
  public boolean isNumberConversion() {
    return true;
  }

  @Override
  public NumberConversion asNumberConversion() {
    return this;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return PrimitiveTypeElement.fromNumericType(to);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfNumberConversion(from, to), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNumberConversion(from, to, source());
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return to == NumericType.BYTE && source().knownToBeBoolean(seen);
  }
}
