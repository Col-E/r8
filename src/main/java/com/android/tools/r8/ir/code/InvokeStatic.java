// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.optimize.MemberRebindingAnalysis.isMemberVisibleFromOriginalContext;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.InvokeStaticRange;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class InvokeStatic extends InvokeMethod {

  private final boolean itf;

  public InvokeStatic(DexMethod target, Value result, List<Value> arguments) {
    this(target, result, arguments, false);
    assert target.asDexReference().asDexMethod().proto.parameters.size() == arguments.size();
  }

  public InvokeStatic(DexMethod target, Value result, List<Value> arguments, boolean itf) {
    super(target, result, arguments);
    this.itf = itf;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public Type getType() {
    return Type.STATIC;
  }

  @Override
  protected String getTypeString() {
    return "Static";
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new InvokeStaticRange(firstRegister, argumentRegisters, getInvokedMethod());
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction = new com.android.tools.r8.code.InvokeStatic(
          argumentRegistersCount,
          getInvokedMethod(),
          individualArgumentRegisters[0],  // C
          individualArgumentRegisters[1],  // D
          individualArgumentRegisters[2],  // E
          individualArgumentRegisters[3],  // F
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
  public DexEncodedMethod lookupSingleTarget(AppInfoWithLiveness appInfo,
      DexType invocationContext) {
    DexMethod method = getInvokedMethod();
    return appInfo.lookupStaticTarget(method);
  }

  @Override
  public Collection<DexEncodedMethod> lookupTargets(AppInfoWithSubtyping appInfo,
      DexType invocationContext) {
    DexEncodedMethod target = appInfo.lookupStaticTarget(getInvokedMethod());
    return target == null ? Collections.emptyList() : Collections.singletonList(target);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forInvokeStatic(getInvokedMethod(), invocationContext);
  }

  @Override
  public InlineAction computeInlining(
      InliningOracle decider,
      DexType invocationContext,
      ClassInitializationAnalysis classInitializationAnalysis) {
    return decider.computeForInvokeStatic(this, invocationContext, classInitializationAnalysis);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInvoke(Opcodes.INVOKESTATIC, getInvokedMethod(), itf));
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      DexType context,
      AppView<? extends AppInfo> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forInvokeStatic(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<? extends AppInfo> appView, DexType context) {
    if (!appView.enableWholeProgramOptimizations()) {
      return true;
    }

    if (appView.options().debug) {
      return true;
    }

    // Find the target and check if the invoke may have side effects.
    if (appView.appInfo().hasLiveness()) {
      AppInfoWithLiveness appInfoWithLiveness = appView.appInfo().withLiveness();
      DexEncodedMethod target = lookupSingleTarget(appInfoWithLiveness, context);
      if (target == null) {
        return true;
      }

      // Verify that the target method is accessible in the current context.
      if (!isMemberVisibleFromOriginalContext(
          appView, context, target.method.holder, target.accessFlags)) {
        return true;
      }

      // Verify that the target method does not have side-effects.
      boolean targetMayHaveSideEffects =
          target.getOptimizationInfo().mayHaveSideEffects()
              && !appInfoWithLiveness.noSideEffects.containsKey(target.method);
      if (targetMayHaveSideEffects) {
        return true;
      }

      // Verify that calling the target method won't lead to class initialization.
      return target.method.holder.classInitializationMayHaveSideEffects(
          appView.appInfo(),
          // Types that are a super type of `context` are guaranteed to be initialized already.
          type -> appView.isSubtype(context, type).isTrue());
    }

    return true;
  }

  @Override
  public boolean canBeDeadCode(AppView<? extends AppInfo> appView, IRCode code) {
    return !instructionMayHaveSideEffects(appView, code.method.method.holder);
  }
}
