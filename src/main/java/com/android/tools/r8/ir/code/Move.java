// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

public class Move extends Instruction {
  private static final String ERROR_MESSAGE =
      "This DEX-specific instruction should not be seen in the CF backend";

  public Move(Value dest, Value src) {
    super(dest, src);
  }

  @Override
  public int opcode() {
    return Opcodes.MOVE;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value dest() {
    return outValue;
  }

  public Value src() {
    return inValues.get(0);
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    throw new Unreachable("as long as we're analyzing SSA IR.");
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.addMove(this);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isMove();
  }

  @Override
  public String toString() {
    return super.toString() + " (" + getOutType() + ")";
  }

  @Override
  public boolean isOutConstant() {
    return src().isConstant();
  }

  @Override
  public ConstInstruction getOutConstantConstInstruction() {
    assert isOutConstant();
    return src().definition.getOutConstantConstInstruction();
  }

  @Override
  public boolean isMove() {
    return true;
  }

  @Override
  public Move asMove() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forMove();
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return src().getType();
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    super.verifyTypes(appView, context, verifyTypesHelper);
    // DebugLocalWrite defines it's own verification of types but should be allowed to call super.
    if (!this.isDebugLocalWrite()) {
      assert src().getType().equals(getOutType());
    }
    return true;
  }
}
