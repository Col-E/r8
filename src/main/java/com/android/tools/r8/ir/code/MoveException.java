// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.utils.InternalOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MoveException extends Instruction {

  public MoveException(Value dest) {
    super(dest);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new com.android.tools.r8.code.MoveException(dest));
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
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    assert other.isMoveException();
    return true;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    assert other.isMoveException();
    return 0;
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
  public boolean canBeDeadCode(IRCode code, InternalOptions options) {
    return !options.debug && options.isGeneratingDex();
  }

  @Override
  public Constraint inliningConstraint(AppInfoWithSubtyping info, DexType invocationContext) {
    // TODO(64432527): Revisit this constraint.
    return Constraint.NEVER;
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
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    Set<DexType> exceptionTypes = new HashSet<>(getBlock().getPredecessors().size());
    for (BasicBlock block : getBlock().getPredecessors()) {
      int size = block.getCatchHandlers().size();
      List<BasicBlock> targets = block.getCatchHandlers().getAllTargets();
      List<DexType> guards = block.getCatchHandlers().getGuards();
      for (int i = 0; i < size; i++) {
        if (targets.get(i) == getBlock()) {
          DexType guard = guards.get(i);
          exceptionTypes.add(
              guard == helper.getFactory().catchAllType
                  ? helper.getFactory().throwableType
                  : guard);
        }
      }
    }
    return helper.join(exceptionTypes);
  }

  @Override
  public TypeLatticeElement evaluate(
      AppInfoWithSubtyping appInfo, Function<Value, TypeLatticeElement> getLatticeElement) {
    return TypeLatticeElement.fromDexType(appInfo, appInfo.dexItemFactory.throwableType, false);
  }
}
