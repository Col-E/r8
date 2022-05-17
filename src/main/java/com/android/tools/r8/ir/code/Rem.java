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
import com.android.tools.r8.ir.analysis.constant.Bottom;
import com.android.tools.r8.ir.analysis.constant.LatticeElement;
import java.util.function.Function;

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
  public boolean instructionTypeCanThrow() {
    return type != NumericType.DOUBLE && type != NumericType.FLOAT;
  }

  @Override
  public LatticeElement evaluate(IRCode code, Function<Value, LatticeElement> getLatticeElement) {
    LatticeElement rightLattice = getLatticeElement.apply(rightValue());
    if (rightLattice.isConst() && !rightLattice.asConst().getConstNumber().isZero()) {
      return super.evaluate(code, getLatticeElement);
    }
    return Bottom.getInstance();
  }

  @Override
  CfArithmeticBinop.Opcode getCfOpcode() {
    return CfArithmeticBinop.Opcode.Rem;
  }
}
