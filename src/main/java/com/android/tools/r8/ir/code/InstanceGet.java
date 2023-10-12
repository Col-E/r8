// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexIget;
import com.android.tools.r8.dex.code.DexIgetBoolean;
import com.android.tools.r8.dex.code.DexIgetByte;
import com.android.tools.r8.dex.code.DexIgetChar;
import com.android.tools.r8.dex.code.DexIgetObject;
import com.android.tools.r8.dex.code.DexIgetShort;
import com.android.tools.r8.dex.code.DexIgetWide;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class InstanceGet extends FieldInstruction implements FieldGet, InstanceFieldInstruction {

  public InstanceGet(Value dest, Value object, DexField field) {
    super(field, dest, object);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static InstanceGet copyOf(IRCode code, InstanceGet original) {
    Value newValue = code.createValue(original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static InstanceGet copyOf(Value newValue, InstanceGet original) {
    assert newValue != original.outValue();
    return InstanceGet.builder()
        .setField(original.getField())
        .setObject(original.object())
        .setOutValue(newValue)
        .build();
  }

  @Override
  public int opcode() {
    return Opcodes.INSTANCE_GET;
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return getField().type.isBooleanType();
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public Value object() {
    assert inValues.size() == 1;
    return inValues.get(0);
  }

  @Override
  public Value value() {
    return outValue;
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getType().isReferenceType();
    assert outValue != null;
    return outValue.getType().isReferenceType();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int destRegister = builder.allocatedRegister(dest(), getNumber());
    int objectRegister = builder.allocatedRegister(object(), getNumber());
    DexInstruction instruction;
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new DexIget(destRegister, objectRegister, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new DexIgetWide(destRegister, objectRegister, field);
        break;
      case OBJECT:
        instruction = new DexIgetObject(destRegister, objectRegister, field);
        break;
      case BOOLEAN:
        instruction = new DexIgetBoolean(destRegister, objectRegister, field);
        break;
      case BYTE:
        instruction = new DexIgetByte(destRegister, objectRegister, field);
        break;
      case CHAR:
        instruction = new DexIgetChar(destRegister, objectRegister, field);
        break;
      case SHORT:
        instruction = new DexIgetShort(destRegister, objectRegister, field);
        break;
      default:
        throw new Unreachable("Unexpected type: " + getType());
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
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
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isInstanceGet()) {
      return false;
    }
    InstanceGet o = other.asInstanceGet();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInstanceGet(getField(), context);
  }

  @Override
  public boolean isFieldGet() {
    return true;
  }

  @Override
  public FieldGet asFieldGet() {
    return this;
  }

  @Override
  public boolean isInstanceFieldInstruction() {
    return true;
  }

  @Override
  public InstanceFieldInstruction asInstanceFieldInstruction() {
    return this;
  }

  @Override
  public boolean isInstanceGet() {
    return true;
  }

  @Override
  public InstanceGet asInstanceGet() {
    return this;
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(getField().type, Nullability.maybeNull(), appView);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return getField().type;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInstanceFieldRead(getField(), builder.resolveField(getField())), this);
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, AppView<?> appView, ProgramMethod context) {
    return object() == value;
  }

  @Override
  public boolean throwsOnNullInput() {
    return true;
  }

  @Override
  public Value getNonNullInput() {
    return object();
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forInstanceGet(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public boolean instructionTypeCanBeCanonicalized() {
    return true;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInstanceFieldRead(getField());
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInstanceGet(getField(), object());
  }

  public static class Builder extends BuilderBase<Builder, InstanceGet> {

    private DexField field;
    private Value object;

    public Builder setField(DexField field) {
      this.field = field;
      return this;
    }

    public Builder setObject(Value object) {
      this.object = object;
      return this;
    }

    @Override
    public InstanceGet build() {
      return amend(new InstanceGet(outValue, object, field));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
