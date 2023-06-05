// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

/**
 * To preserve stack-map table information regarding uninitializedThis flags the
 * UninitializedThisLocalRead is added to every instance-initializer's exits to fake a "read" of
 * this. For more information, see b/183285081
 */
public class UninitializedThisLocalRead extends Instruction {

  private static final String ERROR_MESSAGE =
      "Unexpected attempt to emit/use UninitializedThisLocalRead.";

  public UninitializedThisLocalRead(Value inValue) {
    super(null, inValue);
  }

  @Override
  public int opcode() {
    return Opcodes.UNINITIALIZED_THIS_LOCAL_READ;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
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
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
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
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Non-materializing so no stack values are needed.
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }
}
