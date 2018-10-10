// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.utils.InternalOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MoveException extends Instruction {

  public MoveException(Value dest) {
    super(dest);
    dest.markNeverNull();
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
    return other.isMoveException();
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
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
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

  public static Set<DexType> collectExceptionTypes(
      BasicBlock currentBlock, DexItemFactory dexItemFactory) {
    Set<DexType> exceptionTypes = new HashSet<>(currentBlock.getPredecessors().size());
    for (BasicBlock block : currentBlock.getPredecessors()) {
      int size = block.getCatchHandlers().size();
      List<BasicBlock> targets = block.getCatchHandlers().getAllTargets();
      List<DexType> guards = block.getCatchHandlers().getGuards();
      for (int i = 0; i < size; i++) {
        if (targets.get(i) == currentBlock) {
          DexType guard = guards.get(i);
          exceptionTypes.add(
              guard == DexItemFactory.catchAllType
                  ? dexItemFactory.throwableType
                  : guard);
        }
      }
    }
    return exceptionTypes;
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.join(collectExceptionTypes(getBlock(), helper.getFactory()));
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    Set<DexType> exceptionTypes = collectExceptionTypes(getBlock(), appInfo.dexItemFactory);
    return TypeLatticeElement.join(exceptionTypes.stream(), false, appInfo);
  }
}
