// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexOrInt;
import com.android.tools.r8.dex.code.DexOrInt2Addr;
import com.android.tools.r8.dex.code.DexOrIntLit16;
import com.android.tools.r8.dex.code.DexOrIntLit8;
import com.android.tools.r8.dex.code.DexOrLong;
import com.android.tools.r8.dex.code.DexOrLong2Addr;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import java.util.Set;

public class Or extends LogicalBinop {

  public static Or create(NumericType type, Value dest, Value left, Value right) {
    Or or = new Or(type, dest, left, right);
    or.normalizeArgumentsForCommutativeBinop();
    return or;
  }

  public static Or createNonNormalized(NumericType type, Value dest, Value left, Value right) {
    return new Or(type, dest, left, right);
  }

  private Or(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.OR;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isOr() {
    return true;
  }

  @Override
  public Or asOr() {
    return this;
  }

  @Override
  public boolean isCommutative() {
    return true;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexOrInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexOrLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexOrInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexOrLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexOrIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexOrIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isOr() && other.asOr().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left | right;
  }

  @Override
  AbstractValue foldIntegers(AbstractValue left, AbstractValue right, AppView<?> appView) {
    if (left.isZero()) {
      return right;
    }
    if (right.isZero()) {
      return left;
    }
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      int result =
          foldIntegers(
              left.asSingleNumberValue().getIntValue(), right.asSingleNumberValue().getIntValue());
      return appView.abstractValueFactory().createSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(
              foldIntegers(left.getDefinitelySetIntBits(), right.getDefinitelySetIntBits()),
              left.getDefinitelyUnsetIntBits() & right.getDefinitelyUnsetIntBits());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(left.getDefinitelySetIntBits(), 0);
    }
    if (right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsNumberValue(right.getDefinitelySetIntBits(), 0);
    }
    return AbstractValue.unknown();
  }

  @Override
  long foldLongs(long left, long right) {
    return left | right;
  }

  @Override
  CfLogicalBinop.Opcode getCfOpcode() {
    return CfLogicalBinop.Opcode.Or;
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return leftValue().knownToBeBoolean(seen) && rightValue().knownToBeBoolean(seen);
  }
}
