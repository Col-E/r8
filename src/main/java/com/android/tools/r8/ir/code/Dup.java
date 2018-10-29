// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;

public class Dup extends Instruction {

  public Dup(StackValue destBottom, StackValue destTop, StackValue src) {
    this(new StackValues(destBottom, destTop), src);
  }

  private Dup(StackValues dest, StackValue src) {
    super(dest, src);
  }

  @Override
  public void setOutValue(Value value) {
    assert outValue == null || !outValue.hasUsersInfo() || !outValue.isUsed() ||
        value instanceof StackValues;
    this.outValue = value;
    for (StackValue val : ((StackValues)value).getStackValues()) {
      val.definition = this;
    }
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
  public void buildCf(CfBuilder builder) {
    if (this.inValues.get(0).type.isWide()) {
      builder.add(new CfStackInstruction(Opcode.Dup2));
    } else {
      builder.add(new CfStackInstruction(Opcode.Dup));
    }
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    throw new Unreachable();
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
      InliningConstraints inliningConstraints, DexType invocationContext) {
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
}
