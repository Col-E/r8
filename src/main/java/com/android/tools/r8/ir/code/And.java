// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.dex.code.DexAndInt;
import com.android.tools.r8.dex.code.DexAndInt2Addr;
import com.android.tools.r8.dex.code.DexAndIntLit16;
import com.android.tools.r8.dex.code.DexAndIntLit8;
import com.android.tools.r8.dex.code.DexAndLong;
import com.android.tools.r8.dex.code.DexAndLong2Addr;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import java.util.Set;

public class And extends LogicalBinop {

  public static And create(NumericType type, Value dest, Value left, Value right) {
    And and = new And(type, dest, left, right);
    and.normalizeArgumentsForCommutativeBinop();
    return and;
  }

  public static And createNonNormalized(NumericType type, Value dest, Value left, Value right) {
    return new And(type, dest, left, right);
  }

  private And(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.AND;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isAnd() {
    return true;
  }

  @Override
  public And asAnd() {
    return this;
  }

  @Override
  public boolean isCommutative() {
    return true;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexAndInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexAndLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexAndInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexAndLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexAndIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexAndIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isAnd() && other.asAnd().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left & right;
  }

  @Override
  AbstractValue foldIntegers(AbstractValue left, AbstractValue right, AppView<?> appView) {
    if (left.isZero()) {
      return left;
    }
    if (right.isZero()) {
      return right;
    }
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      int result =
          foldIntegers(
              left.asSingleNumberValue().getIntValue(), right.asSingleNumberValue().getIntValue());
      return appView.abstractValueFactory().createSingleNumberValue(result, getOutType());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(
              foldIntegers(left.getDefinitelySetIntBits(), right.getDefinitelySetIntBits()),
              left.getDefinitelyUnsetIntBits() | right.getDefinitelyUnsetIntBits());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(0, left.getDefinitelyUnsetIntBits());
    }
    if (right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(0, right.getDefinitelyUnsetIntBits());
    }
    return AbstractValue.unknown();
  }

  @Override
  long foldLongs(long left, long right) {
    return left & right;
  }

  @Override
  CfLogicalBinop.Opcode getCfOpcode() {
    return CfLogicalBinop.Opcode.And;
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return leftValue().knownToBeBoolean(seen) && rightValue().knownToBeBoolean(seen);
  }
}
