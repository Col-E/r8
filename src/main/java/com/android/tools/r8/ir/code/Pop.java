// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

public class Pop extends Instruction {

  public Pop(Value src) {
    super(null, src);
    assert src.isValueOnStack();
  }

  @Override
  public int opcode() {
    return Opcodes.POP;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  protected void addInValue(Value value) {
    if (value.hasUsersInfo()) {
      super.addInValue(value);
    } else {
      // Overriding IR addInValue since userinfo is cleared.
      inValues.add(value);
    }
  }

  @Override
  public boolean isPop() {
    return true;
  }

  @Override
  public Pop asPop() {
    return this;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isPop()) {
      return false;
    }
    Pop pop = other.asPop();
    if (getFirstOperand().isDefinedByInstructionSatisfying(Instruction::isInitClass)) {
      InitClass initClass = getFirstOperand().getDefinition().asInitClass();
      if (!pop.getFirstOperand().isDefinedByInstructionSatisfying(Instruction::isInitClass)) {
        return false;
      }
      InitClass otherInitClass = pop.getFirstOperand().getDefinition().asInitClass();
      return initClass.getClassValue() == otherInitClass.getClassValue();
    }
    return !pop.getFirstOperand().isDefinedByInstructionSatisfying(Instruction::isInitClass);
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable();
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forPop();
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    throw new Unreachable("This classfile-specific IR should not be used in LIR.");
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("This classfile-specific IR should not be inserted in the Dex backend.");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(CfStackInstruction.popType(inValues.get(0).outType()), this);
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable("This IR must not be inserted before load and store insertion.");
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    // Pop cannot be dead code as it modifies the stack height.
    return DeadInstructionResult.notDead();
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }
}
