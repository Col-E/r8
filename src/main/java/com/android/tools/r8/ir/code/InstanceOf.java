// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstanceOf;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Set;

public class InstanceOf extends Instruction {

  private final DexType type;

  public InstanceOf(Value dest, Value value, DexType type) {
    super(dest, value);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.INSTANCE_OF;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public DexType type() {
    return type;
  }

  public Value dest() {
    return outValue;
  }

  public Value value() {
    return inValues.get(0);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    int value = builder.allocatedRegister(value(), getNumber());
    builder.addInstanceOf(this, new DexInstanceOf(dest, value, type));
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInstanceOf() && other.asInstanceOf().type == type;
  }

  @Override
  public boolean isInstanceOf() {
    return true;
  }

  @Override
  public InstanceOf asInstanceOf() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInstanceOf(type, context);
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.getInt();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInstanceOf(type), this);
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return true;
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(super.toString());
    return builder.append("; ").append(type.toSourceString()).toString();
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInstanceOf(type);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInstanceOf(type, value());
  }
}
