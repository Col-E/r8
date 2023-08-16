// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexRemDouble;
import com.android.tools.r8.dex.code.DexRemDouble2Addr;
import com.android.tools.r8.dex.code.DexRemFloat;
import com.android.tools.r8.dex.code.DexRemFloat2Addr;
import com.android.tools.r8.dex.code.DexRemInt;
import com.android.tools.r8.dex.code.DexRemInt2Addr;
import com.android.tools.r8.dex.code.DexRemIntLit16;
import com.android.tools.r8.dex.code.DexRemIntLit8;
import com.android.tools.r8.dex.code.DexRemLong;
import com.android.tools.r8.dex.code.DexRemLong2Addr;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class Rem extends ArithmeticBinop {

  public Rem(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.REM;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isRem() {
    return true;
  }

  @Override
  public Rem asRem() {
    return this;
  }

  @Override
  public boolean isCommutative() {
    return false;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexRemInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexRemLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateFloat(int dest, int left, int right) {
    return new DexRemFloat(dest, left, right);
  }

  @Override
  public DexInstruction CreateDouble(int dest, int left, int right) {
    return new DexRemDouble(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexRemInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexRemLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateFloat2Addr(int left, int right) {
    return new DexRemFloat2Addr(left, right);
  }

  @Override
  public DexInstruction CreateDouble2Addr(int left, int right) {
    return new DexRemDouble2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexRemIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexRemIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isRem() && other.asRem().type == type;
  }

  @Override
  public boolean canBeFolded() {
    return super.canBeFolded() && !rightValue().isZero();
  }

  @Override
  int foldIntegers(int left, int right) {
    return left % right;
  }

  @Override
  long foldLongs(long left, long right) {
    return left % right;
  }

  @Override
  float foldFloat(float left, float right) {
    return left % right;
  }

  @Override
  double foldDouble(double left, double right) {
    return left % right;
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
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    AbstractValue rightLattice = abstractValueSupplier.getAbstractValue(rightValue());
    if (!rightLattice.isZero()) {
      return super.getAbstractValue(appView, context, abstractValueSupplier);
    }
    return AbstractValue.unknown();
  }

  @Override
  CfArithmeticBinop.Opcode getCfOpcode() {
    return CfArithmeticBinop.Opcode.Rem;
  }
}
