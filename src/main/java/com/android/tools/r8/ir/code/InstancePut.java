// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexIput;
import com.android.tools.r8.dex.code.DexIputBoolean;
import com.android.tools.r8.dex.code.DexIputByte;
import com.android.tools.r8.dex.code.DexIputChar;
import com.android.tools.r8.dex.code.DexIputObject;
import com.android.tools.r8.dex.code.DexIputShort;
import com.android.tools.r8.dex.code.DexIputWide;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Arrays;

public class InstancePut extends FieldInstruction implements FieldPut, InstanceFieldInstruction {

  public InstancePut(DexField field, Value object, Value value) {
    this(field, object, value, false);
  }

  // During structural changes, IRCode is not valid from IR building until the point where
  // several passes, such as the lens code rewriter, has been run. At this point, it can happen,
  // for example in the context of enum unboxing, that some InstancePut have temporarily
  // a primitive type as the object. Skip assertions in this case.
  public static InstancePut createPotentiallyInvalid(DexField field, Value object, Value value) {
    return new InstancePut(field, object, value, true);
  }

  private InstancePut(DexField field, Value object, Value value, boolean skipAssertion) {
    super(field, null, Arrays.asList(object, value));
    if (!skipAssertion) {
      assert object().verifyCompatible(ValueType.OBJECT);
      assert value().verifyCompatible(ValueType.fromDexType(field.type));
    }
  }

  @Override
  public int opcode() {
    return Opcodes.INSTANCE_PUT;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public int getValueIndex() {
    return 1;
  }

  @Override
  public Value object() {
    return inValues.get(0);
  }

  @Override
  public Value value() {
    return inValues.get(getValueIndex());
  }

  @Override
  public void setValue(Value value) {
    replaceValue(1, value);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int valueRegister = builder.allocatedRegister(value(), getNumber());
    int objectRegister = builder.allocatedRegister(object(), getNumber());
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new DexIput(valueRegister, objectRegister, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new DexIputWide(valueRegister, objectRegister, field);
        break;
      case OBJECT:
        instruction = new DexIputObject(valueRegister, objectRegister, field);
        break;
      case BOOLEAN:
        instruction = new DexIputBoolean(valueRegister, objectRegister, field);
        break;
      case BYTE:
        instruction = new DexIputByte(valueRegister, objectRegister, field);
        break;
      case CHAR:
        instruction = new DexIputChar(valueRegister, objectRegister, field);
        break;
      case SHORT:
        instruction = new DexIputShort(valueRegister, objectRegister, field);
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
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    if (appView.appInfo().hasLiveness()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      AppInfoWithLiveness appInfoWithLiveness = appViewWithLiveness.appInfo();

      FieldResolutionResult resolutionResult = appInfoWithLiveness.resolveField(getField());
      if (internalInstructionInstanceCanThrow(appView, context, assumption, resolutionResult)) {
        return true;
      }

      DexClassAndField field = resolutionResult.getResolutionPair();
      assert field != null : "NoSuchFieldError (resolution failure) should be caught.";

      if (field.getType().isAlwaysNull(appViewWithLiveness)) {
        return false;
      }

      if (appView
          .getAssumeInfoCollection()
          .isMaterializableInAllContexts(appViewWithLiveness, field)) {
        return false;
      }

      return appInfoWithLiveness.isFieldRead(field)
          || isStoringObjectWithFinalizer(appViewWithLiveness, field);
    }

    // In D8, we always have to assume that the field can be read, and thus have side effects.
    return true;
  }

  @Override
  public boolean identicalAfterRegisterAllocation(
      Instruction other, RegisterAllocator allocator, MethodConversionOptions conversionOptions) {
    if (!super.identicalAfterRegisterAllocation(other, allocator, conversionOptions)) {
      return false;
    }

    if (allocator.options().canHaveIncorrectJoinForArrayOfInterfacesBug()) {
      InstancePut instancePut = other.asInstancePut();

      // If the value being written by this instruction is an array, then make sure that the value
      // being written by the other instruction is the exact same value. Otherwise, the verifier
      // may incorrectly join the types of these arrays to Object[].
      if (value().getType().isArrayType() && value() != instancePut.value()) {
        return false;
      }
    }

    return true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isInstancePut()) {
      return false;
    }
    InstancePut o = other.asInstancePut();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "InstancePut instructions define no values.";
    return 0;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInstancePut(getField(), context);
  }

  @Override
  public boolean isFieldPut() {
    return true;
  }

  @Override
  public FieldPut asFieldPut() {
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
  public boolean isInstancePut() {
    return true;
  }

  @Override
  public InstancePut asInstancePut() {
    return this;
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInstanceFieldWrite(getField(), builder.resolveField(getField())), this);
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
    return ClassInitializationAnalysis.InstructionUtils.forInstancePut(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInstanceFieldWrite(getField());
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInstancePut(getField(), object(), value());
  }
}
