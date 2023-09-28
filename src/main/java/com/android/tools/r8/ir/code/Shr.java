// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexShrInt;
import com.android.tools.r8.dex.code.DexShrInt2Addr;
import com.android.tools.r8.dex.code.DexShrIntLit8;
import com.android.tools.r8.dex.code.DexShrLong;
import com.android.tools.r8.dex.code.DexShrLong2Addr;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class Shr extends LogicalBinop {

  public Shr(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.SHR;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  boolean fitsInDexInstruction(Value value) {
    // The shr instruction only has the /lit8 variant.
    return fitsInLit8Instruction(value);
  }

  @Override
  public boolean isShr() {
    return true;
  }

  @Override
  public Shr asShr() {
    return this;
  }

  @Override
  public boolean isCommutative() {
    return false;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexShrInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexShrLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexShrInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexShrLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexShrIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    throw new Unreachable("Unsupported instruction ShrIntLit16");
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isShr() && other.asShr().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left >> right;
  }

  @Override
  AbstractValue foldIntegers(AbstractValue left, AbstractValue right, AppView<?> appView) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue();
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      int result = foldIntegers(left.asSingleNumberValue().getIntValue(), rightConst);
      return appView.abstractValueFactory().createSingleNumberValue(result, getOutType());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(
              foldIntegers(left.getDefinitelySetIntBits(), rightConst),
              foldIntegers(left.getDefinitelyUnsetIntBits(), rightConst));
    }
    return AbstractValue.unknown();
  }

  @Override
  long foldLongs(long left, long right) {
    return left >> right;
  }

  @Override
  CfLogicalBinop.Opcode getCfOpcode() {
    return CfLogicalBinop.Opcode.Shr;
  }
}
