// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.dex.code.DexDivDouble;
import com.android.tools.r8.dex.code.DexDivDouble2Addr;
import com.android.tools.r8.dex.code.DexDivFloat;
import com.android.tools.r8.dex.code.DexDivFloat2Addr;
import com.android.tools.r8.dex.code.DexDivInt;
import com.android.tools.r8.dex.code.DexDivInt2Addr;
import com.android.tools.r8.dex.code.DexDivIntLit16;
import com.android.tools.r8.dex.code.DexDivIntLit8;
import com.android.tools.r8.dex.code.DexDivLong;
import com.android.tools.r8.dex.code.DexDivLong2Addr;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.lightir.LirBuilder;

public class Div extends ArithmeticBinop {

  public Div(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.DIV;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isDiv() {
    return true;
  }

  @Override
  public Div asDiv() {
    return this;
  }

  @Override
  public boolean isCommutative() {
    return false;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexDivInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexDivLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateFloat(int dest, int left, int right) {
    return new DexDivFloat(dest, left, right);
  }

  @Override
  public DexInstruction CreateDouble(int dest, int left, int right) {
    return new DexDivDouble(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexDivInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexDivLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateFloat2Addr(int left, int right) {
    return new DexDivFloat2Addr(left, right);
  }

  @Override
  public DexInstruction CreateDouble2Addr(int left, int right) {
    return new DexDivDouble2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexDivIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexDivIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isDiv() && other.asDiv().type == type;
  }

  @Override
  public boolean canBeFolded() {
    return super.canBeFolded() && !rightValue().isZero();
  }

  @Override
  int foldIntegers(int left, int right) {
    return left / right;
  }

  @Override
  long foldLongs(long left, long right) {
    return left / right;
  }

  @Override
  float foldFloat(float left, float right) {
    return left / right;
  }

  @Override
  double foldDouble(double left, double right) {
    return left / right;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    if (!instructionTypeCanThrow()) {
      return false;
    }
    AbstractValue rightAbstractValue = abstractValueSupplier.getAbstractValue(rightValue());
    if (rightAbstractValue.isSingleNumberValue() && !rightAbstractValue.isZero()) {
      return false;
    }
    if (rightAbstractValue.isDefiniteBitsNumberValue()
        && rightAbstractValue.asDefiniteBitsNumberValue().getDefinitelySetIntBits() != 0) {
      return false;
    }
    return true;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return type != NumericType.DOUBLE && type != NumericType.FLOAT;
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    AbstractValue rightAbstractValue = abstractValueSupplier.getAbstractValue(rightValue());
    if (!rightAbstractValue.isZero()) {
      return super.getAbstractValue(appView, context, abstractValueSupplier);
    }
    return AbstractValue.unknown();
  }

  @Override
  CfArithmeticBinop.Opcode getCfOpcode() {
    return CfArithmeticBinop.Opcode.Div;
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addDiv(type, leftValue(), rightValue());
  }
}
