// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexSput;
import com.android.tools.r8.dex.code.DexSputBoolean;
import com.android.tools.r8.dex.code.DexSputByte;
import com.android.tools.r8.dex.code.DexSputChar;
import com.android.tools.r8.dex.code.DexSputObject;
import com.android.tools.r8.dex.code.DexSputShort;
import com.android.tools.r8.dex.code.DexSputWide;
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

public class StaticPut extends FieldInstruction implements FieldPut, StaticFieldInstruction {

  public StaticPut(Value source, DexField field) {
    super(field, null, source);
  }

  @Override
  public int opcode() {
    return Opcodes.STATIC_PUT;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public int getValueIndex() {
    return 0;
  }

  @Override
  public Value value() {
    assert inValues.size() == 1;
    return inValues.get(getValueIndex());
  }

  @Override
  public void setValue(Value value) {
    replaceValue(0, value);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int src = builder.allocatedRegister(value(), getNumber());
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new DexSput(src, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new DexSputWide(src, field);
        break;
      case OBJECT:
        instruction = new DexSputObject(src, field);
        break;
      case BOOLEAN:
        instruction = new DexSputBoolean(src, field);
        break;
      case BYTE:
        instruction = new DexSputByte(src, field);
        break;
      case CHAR:
        instruction = new DexSputChar(src, field);
        break;
      case SHORT:
        instruction = new DexSputShort(src, field);
        break;
      default:
        throw new Unreachable("Unexpected type: " + getType());
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // This can cause <clinit> to run.
    return true;
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption) {
    if (appView.appInfo().hasLiveness()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      AppInfoWithLiveness appInfoWithLiveness = appViewWithLiveness.appInfo();

      FieldResolutionResult resolutionResult = appInfoWithLiveness.resolveField(getField());
      if (internalInstructionInstanceCanThrow(appView, context, assumption, resolutionResult)) {
        return true;
      }

      DexClassAndField field = resolutionResult.getResolutionPair();
      assert field != null : "NoSuchFieldError (resolution failure) should be caught.";

      boolean isDeadProtoExtensionField =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.isDeadProtoExtensionField(field.getReference()), false);
      if (isDeadProtoExtensionField) {
        return false;
      }

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
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "StaticPut instructions define no values.";
    return 0;
  }

  @Override
  public boolean identicalAfterRegisterAllocation(
      Instruction other, RegisterAllocator allocator, MethodConversionOptions conversionOptions) {
    if (!super.identicalAfterRegisterAllocation(other, allocator, conversionOptions)) {
      return false;
    }

    if (allocator.options().canHaveIncorrectJoinForArrayOfInterfacesBug()) {
      StaticPut staticPut = other.asStaticPut();

      // If the value being written by this instruction is an array, then make sure that the value
      // being written by the other instruction is the exact same value. Otherwise, the verifier
      // may incorrectly join the types of these arrays to Object[].
      if (value().getType().isArrayType() && value() != staticPut.value()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isStaticPut()) {
      return false;
    }
    StaticPut o = other.asStaticPut();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forStaticPut(getField(), context);
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
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
  public boolean isStaticFieldInstruction() {
    return true;
  }

  @Override
  public boolean isStaticPut() {
    return true;
  }

  @Override
  public StaticPut asStaticPut() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfStaticFieldWrite(getField(), builder.resolveField(getField())), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addStaticPut(getField(), value());
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forStaticPut(
        this, clazz, appView, mode, assumption);
  }

  @Override
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
    registry.registerStaticFieldWrite(getField());
  }
}
