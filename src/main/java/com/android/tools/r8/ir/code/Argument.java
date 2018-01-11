// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;

/**
 * Argument pseudo instruction used to introduce values for all arguments for SSA conversion.
 */
public class Argument extends Instruction {

  public Argument(Value outValue) {
    super(outValue);
    outValue.markAsArgument();
    super.setPosition(Position.none());
  }

  @Override
  public void setPosition(Position position) {
    // Arguments never have positional information as they never materialize to actual instructions.
  }

  @Override
  public boolean canBeDeadCode(IRCode code, InternalOptions options) {
    // Never remove argument instructions. That would change the signature of the method.
    // TODO(ager): If we can tell that a method never uses an argument we might be able to
    // rewrite the signature and call-sites.
    return false;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "Argument has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.addArgument(this);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    assert other.isArgument();
    return true;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    assert other.isArgument();
    return 0;
  }

  @Override
  public boolean isArgument() {
    return true;
  }

  @Override
  public Argument asArgument() {
    return this;
  }

  @Override
  public Constraint inliningConstraint(AppInfoWithLiveness info, DexType invocationContext) {
    return Constraint.ALWAYS;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Arguments are defined by locals so nothing to load or store.
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    throw new Unreachable();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.addArgument(this);
  }
}
