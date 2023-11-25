// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexSget;
import com.android.tools.r8.dex.code.DexSgetBoolean;
import com.android.tools.r8.dex.code.DexSgetByte;
import com.android.tools.r8.dex.code.DexSgetChar;
import com.android.tools.r8.dex.code.DexSgetObject;
import com.android.tools.r8.dex.code.DexSgetShort;
import com.android.tools.r8.dex.code.DexSgetWide;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
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

public class StaticGet extends FieldInstruction implements FieldGet, StaticFieldInstruction {

  public StaticGet(Value dest, DexField field) {
    super(field, dest, (Value) null);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static StaticGet copyOf(IRCode code, StaticGet original) {
    Value newValue = code.createValue(original.getOutType(), original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static StaticGet copyOf(Value newValue, StaticGet original) {
    assert newValue != original.outValue();
    return new StaticGet(newValue, original.getField());
  }

  @Override
  public int opcode() {
    return Opcodes.STATIC_GET;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public Value value() {
    return outValue;
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getType().isReferenceType();
    assert outValue != null;
    TypeElement outType = outValue.getType();
    if (outType.isPrimitiveType()) {
      return false;
    }
    if (appView.appInfo().hasLiveness()) {
      if (outType.isClassType()
          && root.getType().isClassType()
          && appView
              .appInfo()
              .withLiveness()
              .inDifferentHierarchy(
                  outType.asClassType().getClassType(),
                  root.getType().asClassType().getClassType())) {
        return false;
      }
    }
    return outType.isReferenceType();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new DexSget(dest, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new DexSgetWide(dest, field);
        break;
      case OBJECT:
        instruction = new DexSgetObject(dest, field);
        break;
      case BOOLEAN:
        instruction = new DexSgetBoolean(dest, field);
        break;
      case BYTE:
        instruction = new DexSgetByte(dest, field);
        break;
      case CHAR:
        instruction = new DexSgetChar(dest, field);
        break;
      case SHORT:
        instruction = new DexSgetShort(dest, field);
        break;
      default:
        throw new Unreachable("Unexpected type: " + getType());
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean instructionTypeCanBeCanonicalized() {
    return true;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // This can cause <clinit> to run.
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    if (appView.getAssumeInfoCollection().isSideEffectFree(getField())) {
      return false;
    }
    return super.instructionInstanceCanThrow(appView, context, abstractValueSupplier, assumption);
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isStaticGet()) {
      return false;
    }
    StaticGet o = other.asStaticGet();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forStaticGet(getField(), context);
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
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
  public boolean isStaticFieldInstruction() {
    return true;
  }

  @Override
  public boolean isStaticGet() {
    return true;
  }

  @Override
  public StaticGet asStaticGet() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfStaticFieldRead(getField(), builder.resolveField(getField())), this);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return getField().type;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(getField().type, Nullability.maybeNull(), appView);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forStaticGet(
        this, clazz, appView, mode, assumption);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return getField().type.isBooleanType();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    DexType holder = getField().holder;
    if (appView.enableWholeProgramOptimizations()) {
      // In R8, check if the class initialization of the holder or any of its ancestor types may
      // have side effects.
      return holder.classInitializationMayHaveSideEffectsInContext(appView, context);
    } else {
      // In D8, this instruction may trigger class initialization if the holder of the field is
      // different from the current context.
      return holder != context.getHolderType();
    }
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerStaticFieldRead(getField());
  }

  public static class Builder extends BuilderBase<Builder, StaticGet> {

    private DexField field;

    public Builder setField(DexClassAndField field) {
      return setField(field.getReference());
    }

    public Builder setField(DexField field) {
      this.field = field;
      return this;
    }

    @Override
    public StaticGet build() {
      return amend(new StaticGet(outValue, field));
    }

    @Override
    public Builder self() {
      return this;
    }
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addStaticGet(getField());
  }
}
