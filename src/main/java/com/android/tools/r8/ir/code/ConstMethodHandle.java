// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstMethodHandle;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexConstMethodHandle;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethodHandle;
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

public class ConstMethodHandle extends ConstInstruction {

  private final DexMethodHandle methodHandle;

  public ConstMethodHandle(Value dest, DexMethodHandle methodHandle) {
    super(dest);
    this.methodHandle = methodHandle;
  }

  @Override
  public int opcode() {
    return Opcodes.CONST_METHOD_HANDLE;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public static ConstMethodHandle copyOf(IRCode code, ConstMethodHandle original) {
    Value newValue =
        new Value(code.valueNumberGenerator.next(), original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static ConstMethodHandle copyOf(Value newValue, ConstMethodHandle original) {
    return new ConstMethodHandle(newValue, original.getValue());
  }

  public Value dest() {
    return outValue;
  }

  public DexMethodHandle getValue() {
    return methodHandle;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new DexConstMethodHandle(dest, methodHandle));
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstMethodHandle(methodHandle), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addConstMethodHandle(methodHandle);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstMethodHandle() && other.asConstMethodHandle().methodHandle == methodHandle;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstMethodHandle has no register arguments.";
    return 0;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forConstMethodHandle();
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + methodHandle + "\"";
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
  public boolean isConstMethodHandle() {
    return true;
  }

  @Override
  public ConstMethodHandle asConstMethodHandle() {
    return this;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(
        appView.dexItemFactory().methodHandleType, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return appView.dexItemFactory().methodHandleType;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerMethodHandle(methodHandle, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }
}
