// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstMethodType;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexConstMethodType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

public class ConstMethodType extends ConstInstruction {

  private final DexProto methodType;

  public ConstMethodType(Value dest, DexProto methodType) {
    super(dest);
    this.methodType = methodType;
  }

  @Override
  public int opcode() {
    return Opcodes.CONST_METHOD_TYPE;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public static ConstMethodType copyOf(IRCode code, ConstMethodType original) {
    Value newValue =
        new Value(code.valueNumberGenerator.next(), original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static ConstMethodType copyOf(Value newValue, ConstMethodType original) {
    return new ConstMethodType(newValue, original.getValue());
  }

  public Value dest() {
    return outValue;
  }

  public DexProto getValue() {
    return methodType;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new DexConstMethodType(dest, methodType));
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstMethodType(methodType), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addConstMethodType(methodType);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstMethodType() && other.asConstMethodType().methodType == methodType;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstMethodType has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + methodType + "\"";
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean isConstMethodType() {
    return true;
  }

  @Override
  public ConstMethodType asConstMethodType() {
    return this;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(
        appView.dexItemFactory().methodTypeType, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return appView.dexItemFactory().methodTypeType;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forConstMethodType();
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerProto(methodType);
  }
}
