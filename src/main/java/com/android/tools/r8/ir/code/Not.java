// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNotInt;
import com.android.tools.r8.dex.code.DexNotLong;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;

public class Not extends Unop {

  public final NumericType type;

  public Not(NumericType type, Value dest, Value source) {
    super(dest, source);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.NOT;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean canBeFolded() {
    return source().isConstant();
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    AbstractValue sourceLattice = abstractValueSupplier.getAbstractValue(source());
    if (sourceLattice.isSingleNumberValue()) {
      SingleNumberValue sourceConst = sourceLattice.asSingleNumberValue();
      long newConst;
      if (this.type == NumericType.INT) {
        newConst = ~sourceConst.getIntValue();
      } else {
        assert this.type == NumericType.LONG;
        newConst = ~sourceConst.getLongValue();
      }
      return appView.abstractValueFactory().createSingleNumberValue(newConst, getOutType());
    }
    return AbstractValue.unknown();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    assert builder.getOptions().canUseNotInstruction();
    DexInstruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    int src = builder.allocatedRegister(source(), getNumber());
    switch (type) {
      case INT:
        instruction = new DexNotInt(dest, src);
        break;
      case LONG:
        instruction = new DexNotLong(dest, src);
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNot() && other.asNot().type == type;
  }

  @Override
  public boolean isNot() {
    return true;
  }

  @Override
  public Not asNot() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // JVM has no Not instruction, they should be replaced by "Load -1, Xor" before building CF.
    throw new Unreachable();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    // JVM has no Not instruction, they should be replaced by "Load -1, Xor" before building CF.
    throw new Unreachable();
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNot(type, source());
  }
}
