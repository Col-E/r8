// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeDirectRange;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.modeling.LibraryMethodReadSetModeling;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public class InvokeDirect extends InvokeMethodWithReceiver {

  // TODO(b/145775365): The interface bit should never be needed once invoke special is in the IR.
  private final boolean isInterface;

  public InvokeDirect(DexMethod target, Value result, List<Value> arguments) {
    this(target, result, arguments, false);
  }

  public InvokeDirect(DexMethod target, Value result, List<Value> arguments, boolean isInterface) {
    super(target, result, arguments);
    this.isInterface = isInterface;
    // invoke-direct <init> should have no out value.
    assert !target.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)
        || result == null;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int opcode() {
    return Opcodes.INVOKE_DIRECT;
  }

  @Override
  public boolean getInterfaceBit() {
    return isInterface;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public InvokeType getType() {
    return InvokeType.DIRECT;
  }

  @Override
  protected String getTypeString() {
    return "Direct";
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new DexInvokeDirectRange(firstRegister, argumentRegisters, getInvokedMethod());
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction =
          new DexInvokeDirect(
              argumentRegistersCount,
              getInvokedMethod(),
              individualArgumentRegisters[0], // C
              individualArgumentRegisters[1], // D
              individualArgumentRegisters[2], // E
              individualArgumentRegisters[3], // F
              individualArgumentRegisters[4]); // G
    }
    addInvokeAndMoveResult(instruction, builder);
  }

  /**
   * Two invokes of a constructor are only allowed to be considered equal if the object
   * they are initializing is the same. Art rejects code that has objects created by
   * different new-instance instructions flow to one constructor invoke.
   */
  public boolean sameConstructorReceiverValue(Invoke other) {
    if (!getInvokedMethod().name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)) {
      return true;
    }
    return inValues.get(0) == other.inValues.get(0);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeDirect() && super.identicalNonValueNonPositionParts(other);
  }

  @Override
  public boolean isInvokeConstructor(DexItemFactory dexItemFactory) {
    return getInvokedMethod().isInstanceInitializer(dexItemFactory);
  }

  @Override
  public boolean isInvokeDirect() {
    return true;
  }

  @Override
  public InvokeDirect asInvokeDirect() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInvokeDirect(getInvokedMethod(), context);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        new CfInvoke(org.objectweb.asm.Opcodes.INVOKESPECIAL, getInvokedMethod(), isInterface),
        this);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forInvokeDirect(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    ProgramMethod context = code.context();
    if (instructionMayHaveSideEffects(
        appView, context, SideEffectAssumption.IGNORE_RECEIVER_FIELD_ASSIGNMENTS)) {
      return DeadInstructionResult.notDead();
    }
    if (!getInvokedMethod().isInstanceInitializer(appView.dexItemFactory())) {
      return DeadInstructionResult.deadIfOutValueIsDead();
    }
    // Super-constructor calls cannot be removed.
    if (getReceiver().getAliasedValue() == code.getThis()) {
      return DeadInstructionResult.notDead();
    }
    // Constructor calls can only be removed if the receiver is dead.
    return DeadInstructionResult.deadIfInValueIsDead(getReceiver());
  }

  @Override
  public AbstractFieldSet readSet(AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    DexMethod invokedMethod = getInvokedMethod();

    // Trivial instance initializers do not read any fields.
    if (appView.dexItemFactory().isConstructor(invokedMethod)) {
      DexClassAndMethod singleTarget = lookupSingleTarget(appView, context);

      // If we have a single target in the program, then use the computed initializer info.
      // If we have a single target in the library, then fallthrough to the library modeling below.
      if (singleTarget != null && singleTarget.isProgramMethod()) {
        return singleTarget
            .getDefinition()
            .getOptimizationInfo()
            .getInstanceInitializerInfo(this)
            .readSet();
      }
    }

    return LibraryMethodReadSetModeling.getModeledReadSetOrUnknown(appView, this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInvokeDirect(getInvokedMethod(), arguments(), isInterface);
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInvokeDirect(getInvokedMethod());
  }

  public static class Builder extends InvokeMethod.Builder<Builder, InvokeDirect> {

    @Override
    public InvokeDirect build() {
      return amend(new InvokeDirect(method, outValue, arguments));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
