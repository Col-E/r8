// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

public class Dup extends Instruction {

  public Dup(StackValue destBottom, StackValue destTop, Value src) {
    this(new StackValues(destBottom, destTop), src);
  }

  private Dup(StackValues dest, Value src) {
    super(dest, src);
    assert src.isValueOnStack() && !(src instanceof StackValues);
  }

  @Override
  public int opcode() {
    return Opcodes.DUP;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public Value setOutValue(Value newOutValue) {
    assert newOutValue instanceof StackValues;
    for (StackValue stackValue : ((StackValues) newOutValue).getStackValues()) {
      stackValue.definition = this;
    }
    return super.setOutValue(newOutValue);
  }

  private StackValue[] getStackValues() {
    return ((StackValues) outValue()).getStackValues();
  }

  public StackValue outBottom() {
    return getStackValues()[0];
  }

  public StackValue outTop() {
    return getStackValues()[1];
  }

  public StackValue src() {
    return (StackValue) inValues.get(0);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("This classfile-specific IR should not be inserted in the Dex backend.");
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    throw new Unreachable("This classfile-specific IR should not be used before finalizing to CF.");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    if (this.inValues.get(0).getType().isWidePrimitive()) {
      builder.add(CfStackInstruction.DUP2, this);
    } else {
      builder.add(CfStackInstruction.DUP, this);
    }
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isDup();
  }

  @Override
  public int maxInValueRegister() {
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forDup();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Intentionally empty. Dup is a stack operation.
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public boolean isDup() {
    return true;
  }

  @Override
  public Dup asDup() {
    return this;
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }
}
