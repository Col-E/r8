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
import com.google.common.collect.ImmutableList;

public class Dup2 extends Instruction {

  public Dup2(
      StackValue destTowMinusThree,
      StackValue destTopMinusTwo,
      StackValue destTopMinusOne,
      StackValue destTop,
      StackValue srcBottom,
      StackValue srcTop) {
    this(
        new StackValues(destTowMinusThree, destTopMinusTwo, destTopMinusOne, destTop),
        srcBottom,
        srcTop);
  }

  private Dup2(StackValues dest, StackValue srcBottom, StackValue srcTop) {
    super(dest, ImmutableList.of(srcBottom, srcTop));
    assert !srcBottom.getTypeLattice().isWide();
    assert !srcTop.getTypeLattice().isWide();
    assert dest.getStackValues().length == 4;
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

  public StackValue outTopMinusThree() {
    return getStackValues()[0];
  }

  public StackValue outTopMinusTwo() {
    return getStackValues()[1];
  }

  public StackValue outTopMinusOne() {
    return getStackValues()[2];
  }

  public StackValue outTop() {
    return getStackValues()[3];
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("This classfile-specific IR should not be inserted in the Dex backend.");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfStackInstruction(Opcode.Dup2));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    throw new Unreachable();
  }

  @Override
  public int compareNonValueParts(Instruction other) {
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
    return inliningConstraints.forDup2();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Intentionally empty. Dup2 is a stack operation.
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public boolean isDup2() {
    return true;
  }

  @Override
  public Dup2 asDup2() {
    return this;
  }
}
