// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.dex.code.DexAddDouble;
import com.android.tools.r8.dex.code.DexAddDouble2Addr;
import com.android.tools.r8.dex.code.DexAddFloat;
import com.android.tools.r8.dex.code.DexAddFloat2Addr;
import com.android.tools.r8.dex.code.DexAddInt;
import com.android.tools.r8.dex.code.DexAddInt2Addr;
import com.android.tools.r8.dex.code.DexAddIntLit16;
import com.android.tools.r8.dex.code.DexAddIntLit8;
import com.android.tools.r8.dex.code.DexAddLong;
import com.android.tools.r8.dex.code.DexAddLong2Addr;
import com.android.tools.r8.dex.code.DexInstruction;

public class Add extends ArithmeticBinop {

  public Add(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.ADD;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isCommutative() {
    return true;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexAddInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexAddLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateFloat(int dest, int left, int right) {
    return new DexAddFloat(dest, left, right);
  }

  @Override
  public DexInstruction CreateDouble(int dest, int left, int right) {
    return new DexAddDouble(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexAddInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexAddLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateFloat2Addr(int left, int right) {
    return new DexAddFloat2Addr(left, right);
  }

  @Override
  public DexInstruction CreateDouble2Addr(int left, int right) {
    return new DexAddDouble2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexAddIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexAddIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isAdd() && other.asAdd().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left + right;
  }

  @Override
  long foldLongs(long left, long right) {
    return left + right;
  }

  @Override
  float foldFloat(float left, float right) {
    return left + right;
  }

  @Override
  double foldDouble(double left, double right) {
    return left + right;
  }

  @Override
  public boolean isAdd() {
    return true;
  }

  @Override
  public Add asAdd() {
    return this;
  }

  @Override
  CfArithmeticBinop.Opcode getCfOpcode() {
    return CfArithmeticBinop.Opcode.Add;
  }
}
