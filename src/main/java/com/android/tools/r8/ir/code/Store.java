// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.FixedLocalValue;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.utils.InternalOptions;

public class Store extends Instruction {

  public Store(Value dest, StackValue src) {
    super(dest, src);
  }

  @Override
  public boolean isStore() {
    return true;
  }

  @Override
  public Store asStore() {
    return this;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return true;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    return 0;
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
  public Constraint inliningConstraint(AppInfoWithSubtyping info, DexType holder) {
    throw new Unreachable();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfStore(outType(), builder.getLocalRegister(outValue)));
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.getType(inValues.get(0));
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Nothing to do. This is only hit because loads and stores are insert for phis.
  }

  @Override
  public boolean canBeDeadCode(IRCode code, InternalOptions options) {
    return !(outValue instanceof FixedLocalValue);
  }
}
