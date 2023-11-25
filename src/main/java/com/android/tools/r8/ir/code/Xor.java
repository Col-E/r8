// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexXorInt;
import com.android.tools.r8.dex.code.DexXorInt2Addr;
import com.android.tools.r8.dex.code.DexXorIntLit16;
import com.android.tools.r8.dex.code.DexXorIntLit8;
import com.android.tools.r8.dex.code.DexXorLong;
import com.android.tools.r8.dex.code.DexXorLong2Addr;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import java.util.Set;

public class Xor extends LogicalBinop {

  public static Xor create(NumericType type, Value dest, Value left, Value right) {
    Xor xor = new Xor(type, dest, left, right);
    xor.normalizeArgumentsForCommutativeBinop();
    return xor;
  }

  public static Xor createNonNormalized(NumericType type, Value dest, Value left, Value right) {
    return new Xor(type, dest, left, right);
  }

  private Xor(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.XOR;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isXor() {
    return true;
  }

  @Override
  public Xor asXor() {
    return this;
  }

  @Override
  public boolean isCommutative() {
    return true;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexXorInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexXorLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexXorInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexXorLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexXorIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexXorIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isXor() && other.asXor().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left ^ right;
  }

  @Override
  AbstractValue foldIntegers(AbstractValue left, AbstractValue right, AppView<?> appView) {
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
              (left.getDefinitelySetIntBits() & right.getDefinitelyUnsetIntBits())
                  | (left.getDefinitelyUnsetIntBits() & right.getDefinitelySetIntBits()),
              (left.getDefinitelySetIntBits() & right.getDefinitelySetIntBits())
                  | (left.getDefinitelyUnsetIntBits() & right.getDefinitelyUnsetIntBits()));
    }
    return AbstractValue.unknown();
  }

  @Override
  long foldLongs(long left, long right) {
    return left ^ right;
  }

  @Override
  CfLogicalBinop.Opcode getCfOpcode() {
    return CfLogicalBinop.Opcode.Xor;
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return leftValue().knownToBeBoolean(seen) && rightValue().knownToBeBoolean(seen);
  }
}
