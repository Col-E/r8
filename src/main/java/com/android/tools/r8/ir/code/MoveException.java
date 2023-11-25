// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexMoveException;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.InternalOptions;

public class MoveException extends Instruction {
  private final DexType exceptionType;
  private final InternalOptions options;

  public MoveException(Value dest, DexType exceptionType, InternalOptions options) {
    super(dest);
    this.exceptionType = exceptionType;
    this.options = options;
  }

  @Override
  public int opcode() {
    return Opcodes.MOVE_EXCEPTION;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new DexMoveException(dest));
  }

  @Override
  public int maxInValueRegister() {
    assert false : "MoveException has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isMoveException()) {
      return false;
    }
    if (options.canHaveExceptionTypeBug()) {
      return other.asMoveException().exceptionType == exceptionType;
    }
    return true;
  }

  @Override
  public boolean isMoveException() {
    return true;
  }

  @Override
  public MoveException asMoveException() {
    return this;
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    InternalOptions options = appView.options();
    if (options.debug
        || code.context().getOrComputeReachabilitySensitive(appView)
        || !code.getConversionOptions().isGeneratingDex()) {
      return DeadInstructionResult.notDead();
    }
    return DeadInstructionResult.deadIfOutValueIsDead();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forMoveException();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    // Nothing to do. The exception is implicitly pushed on the stack.
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return exceptionType;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(exceptionType, Nullability.definitelyNotNull(), appView);
  }

  public DexType getExceptionType() {
    return exceptionType;
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addMoveException(exceptionType);
  }
}
