// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.dex.code.DexReturnObject;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.dex.code.DexReturnWide;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

public class Return extends JumpInstruction {

  public Return() {
    super();
  }

  public Return(Value value) {
    super(value);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int opcode() {
    return Opcodes.RETURN;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public boolean isReturnVoid() {
    return inValues.size() == 0;
  }

  public TypeElement getReturnType() {
    assert !isReturnVoid();
    return returnValue().getType();
  }

  public boolean hasReturnValue() {
    return !isReturnVoid();
  }

  public Value returnValue() {
    assert !isReturnVoid();
    return inValues.get(0);
  }

  public DexInstruction createDexInstruction(DexBuilder builder) {
    if (isReturnVoid()) {
      return new DexReturnVoid();
    }
    int register = builder.allocatedRegister(returnValue(), getNumber());
    TypeElement returnType = getReturnType();
    if (returnType.isReferenceType()) {
      return new DexReturnObject(register);
    }
    if (returnType.isSinglePrimitive()) {
      return new DexReturn(register);
    }
    if (returnType.isWidePrimitive()) {
      return new DexReturnWide(register);
    }
    throw new Unreachable();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.addReturn(this, createDexInstruction(builder));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isReturn()) {
      return false;
    }
    Return o = other.asReturn();
    if (isReturnVoid()) {
      return o.isReturnVoid();
    }
    return getReturnType().isValueTypeCompatible(o.getReturnType());
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "Return defines no values.";
    return 0;
  }

  @Override
  public boolean isReturn() {
    return true;
  }

  @Override
  public Return asReturn() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forReturn();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    if (!isReturnVoid()) {
      helper.loadInValues(this, it);
    }
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        isReturnVoid() ? new CfReturnVoid() : new CfReturn(ValueType.fromType(getReturnType())),
        this);
  }

  public static class Builder extends BuilderBase<Builder, Return> {

    private Value returnValue = null;

    public Builder setReturnValue(Value returnValue) {
      this.returnValue = returnValue;
      return self();
    }

    @Override
    public Return build() {
      return amend(returnValue == null ? new Return() : new Return(returnValue));
    }

    @Override
    public Builder self() {
      return this;
    }
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    if (hasReturnValue()) {
      builder.addReturn(returnValue());
    } else {
      builder.addReturnVoid();
    }
  }
}
