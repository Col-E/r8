// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;

public abstract class LogicalBinop extends Binop {

  LogicalBinop(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  public abstract DexInstruction CreateInt(int dest, int left, int right);

  public abstract DexInstruction CreateLong(int dest, int left, int right);

  public abstract DexInstruction CreateInt2Addr(int left, int right);

  public abstract DexInstruction CreateLong2Addr(int left, int right);

  public abstract DexInstruction CreateIntLit8(int dest, int left, int constant);

  public abstract DexInstruction CreateIntLit16(int dest, int left, int constant);

  @Override
  public boolean canBeFolded() {
    return leftValue().isConstant() && rightValue().isConstant();
  }

  @Override
  public boolean needsValueInRegister(Value value) {
    // Always require the left value in a register. If left and right are the same value, then
    // both will use its register.
    if (value == leftValue()) {
      return true;
    }
    assert value == rightValue();
    return !fitsInDexInstruction(value);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    // TODO(ager, sgjesse): For now the left operand is always a value. With constant propagation
    // that will change.
    int left = builder.allocatedRegister(leftValue(), getNumber());
    int dest = builder.allocatedRegister(outValue, getNumber());
    DexInstruction instruction;
    if (isTwoAddr(builder.getRegisterAllocator())) {
      int right = builder.allocatedRegister(rightValue(), getNumber());
      if (left != dest) {
        assert isCommutative();
        assert right == dest;
        right = left;
      }
      switch (type) {
        case INT:
          instruction = CreateInt2Addr(dest, right);
          break;
        case LONG:
          instruction = CreateLong2Addr(dest, right);
          break;
        default:
          throw new Unreachable("Unexpected type " + type);
      }
    } else if (!needsValueInRegister(rightValue())) {
      assert fitsInDexInstruction(rightValue());
      ConstNumber right = rightValue().getConstInstruction().asConstNumber();
      if (right.is8Bit()) {
        instruction = CreateIntLit8(dest, left, right.getIntValue());
      } else {
        assert right.is16Bit();
        instruction = CreateIntLit16(dest, left, right.getIntValue());
      }
    } else {
      int right = builder.allocatedRegister(rightValue(), getNumber());
      switch (type) {
        case INT:
          instruction = CreateInt(dest, left, right);
          break;
        case LONG:
          instruction = CreateLong(dest, left, right);
          break;
        default:
          throw new Unreachable("Unexpected type " + type);
      }
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean isLogicalBinop() {
    return true;
  }

  @Override
  public LogicalBinop asLogicalBinop() {
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    AbstractValue leftAbstractValue = abstractValueSupplier.getAbstractValue(leftValue());
    AbstractValue rightAbstractValue = abstractValueSupplier.getAbstractValue(rightValue());
    if (leftAbstractValue.isSingleNumberValue() && rightAbstractValue.isSingleNumberValue()) {
      SingleNumberValue leftConst = leftAbstractValue.asSingleNumberValue();
      SingleNumberValue rightConst = rightAbstractValue.asSingleNumberValue();
      long newConst;
      if (type == NumericType.INT) {
        newConst = foldIntegers(leftConst.getIntValue(), rightConst.getIntValue());
      } else {
        assert type == NumericType.LONG;
        long right;
        if (isShl() || isShr() || isUshr()) {
          // Right argument for shl, shr and ushr is always of type single.
          right = rightConst.getIntValue();
        } else {
          right = rightConst.getLongValue();
        }
        newConst = foldLongs(leftConst.getLongValue(), right);
      }
      return appView.abstractValueFactory().createSingleNumberValue(newConst, getOutType());
    } else {
      if (type == NumericType.INT) {
        return foldIntegers(leftAbstractValue, rightAbstractValue, appView);
      }
    }
    return AbstractValue.unknown();
  }

  abstract CfLogicalBinop.Opcode getCfOpcode();

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(CfLogicalBinop.operation(getCfOpcode(), type), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addLogicalBinop(getCfOpcode(), type, leftValue(), rightValue());
  }
}
