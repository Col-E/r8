// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public abstract class InvokeMethodWithReceiver extends InvokeMethod {

  InvokeMethodWithReceiver(DexMethod target, Value result, List<Value> arguments) {
    super(target, result, arguments);
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
  public boolean verifyTypes(AppView<?> appView) {
    assert super.verifyTypes(appView);

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
    if (!getReceiver().definition.isAssumeDynamicType()) {
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
}
