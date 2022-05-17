// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexMulDouble;
import com.android.tools.r8.dex.code.DexMulDouble2Addr;
import com.android.tools.r8.dex.code.DexMulFloat;
import com.android.tools.r8.dex.code.DexMulFloat2Addr;
import com.android.tools.r8.dex.code.DexMulInt;
import com.android.tools.r8.dex.code.DexMulInt2Addr;
import com.android.tools.r8.dex.code.DexMulIntLit16;
import com.android.tools.r8.dex.code.DexMulIntLit8;
import com.android.tools.r8.dex.code.DexMulLong;
import com.android.tools.r8.dex.code.DexMulLong2Addr;

public class Mul extends ArithmeticBinop {

  public Mul(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.MUL;
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
    // Flip arguments if dest and right are the same to work around x86 code generation bug on
    // Android L. See https://android-review.googlesource.com/#/c/114932/ for the fix for Android
    // M.
    return dest == right ? new DexMulInt(dest, right, left) : new DexMulInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    // Flip arguments if dest and right are the same to work around x86 code generation bug on
    // Android L. See https://android-review.googlesource.com/#/c/114932/ for the fix for Android
    // M.
    return dest == right ? new DexMulLong(dest, right, left) : new DexMulLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateFloat(int dest, int left, int right) {
    // Flip arguments if dest and right are the same to work around x86 code generation bug on
    // Android L. See https://android-review.googlesource.com/#/c/114932/ for the fix for Android
    // M.
    return dest == right ? new DexMulFloat(dest, right, left) : new DexMulFloat(dest, left, right);
  }

  @Override
  public DexInstruction CreateDouble(int dest, int left, int right) {
    // Flip arguments if dest and right are the same to work around x86 code generation bug on
    // Android L. See https://android-review.googlesource.com/#/c/114932/ for the fix for Android
    // M.
    return dest == right
        ? new DexMulDouble(dest, right, left)
        : new DexMulDouble(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexMulInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexMulLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateFloat2Addr(int left, int right) {
    return new DexMulFloat2Addr(left, right);
  }

  @Override
  public DexInstruction CreateDouble2Addr(int left, int right) {
    return new DexMulDouble2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    return new DexMulIntLit8(dest, left, constant);
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    return new DexMulIntLit16(dest, left, constant);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isMul() && other.asMul().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left * right;
  }

  @Override
  long foldLongs(long left, long right) {
    return left * right;
  }

  @Override
  float foldFloat(float left, float right) {
    return left * right;
  }

  @Override
  double foldDouble(double left, double right) {
    return left * right;
  }

  @Override
  public boolean isMul() {
    return true;
  }

  @Override
  public Mul asMul() {
    return this;
  }

  @Override
  CfArithmeticBinop.Opcode getCfOpcode() {
    return CfArithmeticBinop.Opcode.Mul;
  }
}
