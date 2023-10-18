// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import java.util.List;

public abstract class InvokeMethodWithReceiver extends InvokeMethod {

  InvokeMethodWithReceiver(DexMethod target, Value result, List<Value> arguments) {
    super(target, result, arguments);
  }

  public Iterable<Value> getNonReceiverArguments() {
    return Iterables.skip(arguments(), 1);
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

  /**
   * If an invoke-virtual targets a private method in the current class overriding will not apply
   * (see JVM 11 spec on method selection 5.4.6. In previous jvm specs this was not explicitly
   * stated, but derived from method resolution 5.4.3.3 and overriding 5.4.5).
   *
   * <p>An invoke-interface can in the same way target a private method.
   *
   * <p>For desugaring we use invoke-direct instead. We need to do this as the Android Runtime will
   * not allow invoke-virtual of a private method.
   */
  @SuppressWarnings("ReferenceEquality")
  protected boolean isPrivateMethodInvokedOnSelf(DexBuilder builder) {
    DexMethod method = getInvokedMethod();
    if (method.getHolderType()
        != builder.getRegisterAllocator().getProgramMethod().getHolderType()) {
      return false;
    }
    DexEncodedMethod directTarget =
        builder.getRegisterAllocator().getProgramMethod().getHolder().lookupDirectMethod(method);
    if (directTarget != null && !directTarget.isStatic()) {
      assert method.holder == directTarget.getHolderType();
      assert directTarget.getReference() == method;
      return true;
    }
    return false;
  }

  protected boolean isPrivateNestMethodInvoke(DexBuilder builder) {
    if (!builder.getOptions().emitNestAnnotationsInDex) {
      return false;
    }
    DexProgramClass holder = builder.getProgramMethod().getHolder();
    if (!holder.isInANest()) {
      return false;
    }
    DexClassAndMethod target = builder.appView.appInfo().definitionFor(getInvokedMethod());
    // Nest completeness for input is checked before starting to write DEX, so if target is null
    // it is not in a nest with the holder of the method with this invoke.
    if (target == null || target.getHolder().isLibraryClass()) {
      return false;
    }
    if (!target.getAccessFlags().isPrivate()) {
      return false;
    }
    return holder.isInSameNest(target.getHolder());
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
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    assert super.verifyTypes(appView, context, verifyTypesHelper);

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
        assert appViewWithLiveness
                    .appInfo()
                    .isSubtype(receiverLowerBoundType.getClassType(), refinedReceiverType)
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
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
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

    assert appView.appInfo().hasClassHierarchy();
    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();

    SingleResolutionResult<?> resolutionResult =
        resolveMethod(appViewWithClassHierarchy).asSingleResolution();
    if (resolutionResult == null) {
      return true;
    }

    // Verify that the target method is accessible in the current context.
    if (resolutionResult.isAccessibleFrom(context, appViewWithClassHierarchy).isPossiblyFalse()) {
      return true;
    }

    if (assumption.canAssumeInvokedMethodDoesNotHaveSideEffects()) {
      return false;
    }

    DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
    if (appView.getAssumeInfoCollection().isSideEffectFree(getInvokedMethod())
        || appView.getAssumeInfoCollection().isSideEffectFree(resolvedMethod)) {
        return false;
      }

    // Find the target and check if the invoke may have side effects.
    DexClassAndMethod singleTarget = lookupSingleTarget(appView, context);
    MethodOptimizationInfo optimizationInfo =
        resolutionResult.getOptimizationInfo(appView, this, singleTarget);
    if (!optimizationInfo.mayHaveSideEffects(this, appView.options())) {
      return false;
    }

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
    if (appView.getAssumeInfoCollection().isSideEffectFree(singleTarget)) {
      return false;
    }

    if (assumption.canIgnoreInstanceFieldAssignmentsToReceiver()
        && singleTarget.getDefinition().isInstanceInitializer()) {
      assert isInvokeDirect();
      InstanceInitializerInfo initializerInfo =
          optimizationInfo.getInstanceInitializerInfo(asInvokeDirect());
      if (!initializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
        return !isInvokeDirect();
      }
    }

    return true;
  }
}
