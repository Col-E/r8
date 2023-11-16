// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;


import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeStaticRange;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public class InvokeStatic extends InvokeMethod {

  private final boolean isInterface;

  public InvokeStatic(DexMethod target, Value result, List<Value> arguments) {
    this(target, result, arguments, false);
    assert target.proto.parameters.size() == arguments.size();
  }

  public InvokeStatic(DexMethod target, Value result, List<Value> arguments, boolean isInterface) {
    super(target, result, arguments);
    this.isInterface = isInterface;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean getInterfaceBit() {
    return isInterface;
  }

  @Override
  public int opcode() {
    return Opcodes.INVOKE_STATIC;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public InvokeType getType() {
    return InvokeType.STATIC;
  }

  @Override
  protected String getTypeString() {
    return "Static";
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new DexInvokeStaticRange(firstRegister, argumentRegisters, getInvokedMethod());
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction =
          new DexInvokeStatic(
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

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeStatic() && super.identicalNonValueNonPositionParts(other);
  }

  @Override
  public boolean isInvokeStatic() {
    return true;
  }

  @Override
  public InvokeStatic asInvokeStatic() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInvokeStatic(getInvokedMethod(), context);
  }

  @Override
  public InlineAction.Builder computeInlining(
      ProgramMethod singleTarget,
      DefaultInliningOracle decider,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return decider.computeForInvokeStatic(
        this, singleTarget, classInitializationAnalysis, whyAreYouNotInliningReporter);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        new CfInvoke(org.objectweb.asm.Opcodes.INVOKESTATIC, getInvokedMethod(), isInterface),
        this);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forInvokeStatic(
        this, clazz, context, appView, mode, assumption);
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    if (!appView.enableWholeProgramOptimizations()) {
      return true;
    }

    if (appView.options().debug) {
      return true;
    }

    // Check if it is a call to one of library methods that are known to be side-effect free.
    if (appView
        .getLibraryMethodSideEffectModelCollection()
        .isCallToSideEffectFreeFinalMethod(this)) {
      return false;
    }

    // Find the target and check if the invoke may have side effects.
    if (!appView.appInfo().hasLiveness()) {
      return true;
    }

    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
    SingleResolutionResult<?> resolutionResult =
        appViewWithLiveness
            .appInfo()
            .resolveMethodLegacy(getInvokedMethod(), isInterface)
            .asSingleResolution();

    // Verify that the target method is present.
    if (resolutionResult == null) {
      return true;
    }

    DexClassAndMethod singleTarget = resolutionResult.getResolutionPair();
    assert singleTarget != null;

    // Verify that the target method is static and accessible.
    if (!singleTarget.getDefinition().isStatic()
        || resolutionResult.isAccessibleFrom(context, appViewWithLiveness).isPossiblyFalse()) {
      return true;
    }

    // Verify that the target method does not have side-effects.
    if (appView.getAssumeInfoCollection().isSideEffectFree(singleTarget)) {
      return false;
    }

    if (singleTarget.getOptimizationInfo().mayHaveSideEffects(this, appView.options())) {
      return true;
    }

    if (assumption.canAssumeClassIsAlreadyInitialized()) {
      return false;
    }

    return singleTarget
        .getHolder()
        .classInitializationMayHaveSideEffectsInContext(appView, context);
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInvokeStatic(getInvokedMethod());
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInvokeStatic(getInvokedMethod(), arguments(), isInterface);
  }

  public static class Builder extends InvokeMethod.Builder<Builder, InvokeStatic> {

    @Override
    public InvokeStatic build() {
      assert arguments != null;
      assert method != null;
      assert method.getArity() == arguments.size();
      assert outValue == null || !method.getReturnType().isVoidType();
      return amend(new InvokeStatic(method, outValue, arguments));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
