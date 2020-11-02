// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public abstract class InvokeMethodWithReceiver extends InvokeMethod {

  InvokeMethodWithReceiver(DexMethod target, Value result, List<Value> arguments) {
    super(target, result, arguments);
  }

  public boolean hasRefinedReceiverLowerBoundType(AppView<AppInfoWithLiveness> appView) {
    assert isInvokeMethodWithDynamicDispatch();
    return getReceiver().getDynamicLowerBoundType(appView) != null;
  }

  public boolean hasRefinedReceiverUpperBoundType(AppView<AppInfoWithLiveness> appView) {
    assert isInvokeMethodWithDynamicDispatch();
    DexType staticReceiverType = getInvokedMethod().holder;
    DexType refinedReceiverType = TypeAnalysis.getRefinedReceiverType(appView, this);
    return refinedReceiverType != staticReceiverType;
  }

  @Override
  public boolean isInvokeMethodWithReceiver() {
    return true;
  }

  @Override
  public InvokeMethodWithReceiver asInvokeMethodWithReceiver() {
    return this;
  }

  public Value getReceiver() {
    assert inValues.size() > 0;
    return inValues.get(0);
  }

  @Override
  public final InlineAction computeInlining(
      ProgramMethod singleTarget,
      Reason reason,
      DefaultInliningOracle decider,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return decider.computeForInvokeWithReceiver(
        this, singleTarget, reason, whyAreYouNotInliningReporter);
  }

  @Override
  public final DexClassAndMethod lookupSingleTarget(AppView<?> appView, ProgramMethod context) {
    TypeElement receiverUpperBoundType = null;
    ClassTypeElement receiverLowerBoundType = null;
    if (appView.enableWholeProgramOptimizations()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      receiverUpperBoundType = getReceiver().getDynamicUpperBoundType(appViewWithLiveness);
      receiverLowerBoundType = getReceiver().getDynamicLowerBoundType(appViewWithLiveness);
    }
    return lookupSingleTarget(appView, context, receiverUpperBoundType, receiverLowerBoundType);
  }

  public abstract DexClassAndMethod lookupSingleTarget(
      AppView<?> appView,
      ProgramMethod context,
      TypeElement receiverUpperBoundType,
      ClassTypeElement receiverLowerBoundType);

  public final ProgramMethod lookupSingleProgramTarget(
      AppView<?> appView,
      ProgramMethod context,
      TypeElement receiverUpperBoundType,
      ClassTypeElement receiverLowerBoundType) {
    return DexClassAndMethod.asProgramMethodOrNull(
        lookupSingleTarget(appView, context, receiverUpperBoundType, receiverLowerBoundType));
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, AppView<?> appView, ProgramMethod context) {
    return value == getReceiver() || super.throwsNpeIfValueIsNull(value, appView, context);
  }

  @Override
  public boolean throwsOnNullInput() {
    return true;
  }

  @Override
  public Value getNonNullInput() {
    return getReceiver();
  }

  @Override
  public boolean verifyTypes(AppView<?> appView, VerifyTypesHelper verifyTypesHelper) {
    assert super.verifyTypes(appView, verifyTypesHelper);

    Value receiver = getReceiver();
    TypeElement receiverType = receiver.getType();
    assert receiverType.isPreciseType();

    if (appView.appInfo().hasLiveness()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      ClassTypeElement receiverLowerBoundType =
          receiver.getDynamicLowerBoundType(appViewWithLiveness);
      if (receiverLowerBoundType != null) {
        DexType refinedReceiverType =
            TypeAnalysis.getRefinedReceiverType(appViewWithLiveness, this);
        assert receiverLowerBoundType.getClassType() == refinedReceiverType
                || appView.options().testing.allowTypeErrors
                || receiver.getDynamicUpperBoundType(appViewWithLiveness).isNullType()
                || receiverLowerBoundType.isBasedOnMissingClass(appViewWithLiveness)
                || upperBoundAssumedByCallSiteOptimizationAndNoLongerInstantiated(
                    appViewWithLiveness, refinedReceiverType, receiverLowerBoundType.getClassType())
            : "The receiver lower bound does not match the receiver type";
      }
    }

    return true;
  }

  private boolean upperBoundAssumedByCallSiteOptimizationAndNoLongerInstantiated(
      AppView<AppInfoWithLiveness> appViewWithLiveness,
      DexType upperBoundType,
      DexType lowerBoundType) {
    // Check that information came from the CallSiteOptimization.
    if (!getReceiver().getAliasedValue().isArgument()) {
      return false;
    }
    // Check that the receiver information comes from a dynamic type.
    if (!getReceiver()
        .isDefinedByInstructionSatisfying(Instruction::isAssumeWithDynamicTypeAssumption)) {
      return false;
    }
    // Now, it can be that the upper bound is more precise than the lower:
    // class A { }
    // class B extends A { }
    //
    // class Main {
    //   new B();
    // }
    //
    // Above, the callsite optimization will register that A.<init> will be called with an argument
    // of type B and put B in as the dynamic upper bound type. However, we can also class-inline B,
    // thereby removing the instantiation, making A effectively final.
    // TODO(b/154822960): Perhaps we should not process this code at all?
    DexProgramClass upperBound = appViewWithLiveness.definitionForProgramType(upperBoundType);
    if (upperBound == null) {
      return false;
    }
    if (appViewWithLiveness.appInfo().isInstantiatedDirectlyOrIndirectly(upperBound)) {
      return false;
    }
    DexClass lowerBound = appViewWithLiveness.definitionFor(lowerBoundType);
    return lowerBound != null && lowerBound.isEffectivelyFinal(appViewWithLiveness);
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption) {
    if (appView.options().debug) {
      return true;
    }

    // Check if it could throw a NullPointerException as a result of the receiver being null.
    Value receiver = getReceiver();
    if (!assumption.canAssumeReceiverIsNotNull() && receiver.getType().isNullable()) {
      return true;
    }

    if (getInvokedMethod().holder.isArrayType()
        && getInvokedMethod().match(appView.dexItemFactory().objectMembers.clone)) {
      return !isInvokeVirtual();
    }

    // Check if it is a call to one of library methods that are known to be side-effect free.
    if (appView
        .getLibraryMethodSideEffectModelCollection()
        .isCallToSideEffectFreeFinalMethod(this)) {
      return false;
    }

    if (!appView.enableWholeProgramOptimizations()) {
      return true;
    }

    assert appView.appInfo().hasLiveness();
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();

    SingleResolutionResult resolutionResult =
        appViewWithLiveness
            .appInfo()
            .resolveMethod(getInvokedMethod(), getInterfaceBit())
            .asSingleResolution();
    if (resolutionResult == null) {
      return true;
    }

    // Verify that the target method is accessible in the current context.
    if (resolutionResult
        .isAccessibleFrom(context, appViewWithLiveness.appInfo())
        .isPossiblyFalse()) {
      return true;
    }

    if (assumption.canAssumeInvokedMethodDoesNotHaveSideEffects()) {
      return false;
    }

    DexEncodedMethod resolvedMethod = resolutionResult.getResolvedMethod();
    if (appViewWithLiveness.appInfo().noSideEffects.containsKey(getInvokedMethod())
        || appViewWithLiveness.appInfo().noSideEffects.containsKey(resolvedMethod.getReference())) {
      return false;
    }

    // Find the target and check if the invoke may have side effects.
    DexClassAndMethod singleTarget = lookupSingleTarget(appViewWithLiveness, context);
    if (singleTarget == null) {
      return true;
    }

    if (singleTarget.isLibraryMethod()
        && appView
            .getLibraryMethodSideEffectModelCollection()
            .isSideEffectFree(this, singleTarget.asLibraryMethod())) {
      return false;
    }

    // Verify that the target method does not have side-effects.
    if (appViewWithLiveness.appInfo().noSideEffects.containsKey(singleTarget.getReference())) {
      return false;
    }

    DexEncodedMethod singleTargetDefinition = singleTarget.getDefinition();
    MethodOptimizationInfo optimizationInfo = singleTargetDefinition.getOptimizationInfo();
    if (singleTargetDefinition.isInstanceInitializer()) {
      InstanceInitializerInfo initializerInfo = optimizationInfo.getInstanceInitializerInfo();
      if (!initializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
        return !isInvokeDirect();
      }
    }

    return optimizationInfo.mayHaveSideEffects();
  }
}
