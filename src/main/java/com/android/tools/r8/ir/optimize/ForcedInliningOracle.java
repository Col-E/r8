// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineResult;
import com.android.tools.r8.ir.optimize.Inliner.InlineeWithReason;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;
import java.util.Optional;

final class ForcedInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProgramMethod method;
  private final Map<? extends InvokeMethod, Inliner.InliningInfo> invokesToInline;

  ForcedInliningOracle(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      Map<? extends InvokeMethod, Inliner.InliningInfo> invokesToInline) {
    this.appView = appView;
    this.method = method;
    this.invokesToInline = invokesToInline;
  }

  @Override
  public AppView<AppInfoWithLiveness> appView() {
    return appView;
  }

  @Override
  public boolean isForcedInliningOracle() {
    return true;
  }

  @Override
  public boolean passesInliningConstraints(
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod candidate,
      Optional<InliningIRProvider> inliningIRProvider,
      Reason reason,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return true;
  }

  @Override
  public ProgramMethod lookupSingleTarget(InvokeMethod invoke, ProgramMethod context) {
    Inliner.InliningInfo info = invokesToInline.get(invoke);
    if (info != null) {
      return info.target;
    }
    return invoke.lookupSingleProgramTarget(appView, context);
  }

  @Override
  public InlineResult computeInlining(
      IRCode code,
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      ProgramMethod context,
      ClassInitializationAnalysis classInitializationAnalysis,
      InliningIRProvider inliningIRProvider,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    InlineAction action = computeForInvoke(invoke, resolutionResult, whyAreYouNotInliningReporter);
    if (action == null) {
      return null;
    }
    if (!setDowncastTypeIfNeeded(appView, action, invoke, singleTarget, context)) {
      return null;
    }
    return action;
  }

  @SuppressWarnings("ReferenceEquality")
  private InlineAction computeForInvoke(
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    Inliner.InliningInfo info = invokesToInline.get(invoke);
    if (info == null) {
      return null;
    }
    assert method.getDefinition() != info.target.getDefinition();
    assert passesInliningConstraints(
        invoke,
        resolutionResult,
        info.target,
        Optional.empty(),
        Reason.FORCE,
        whyAreYouNotInliningReporter);
    return new InlineAction(info.target, invoke, Reason.FORCE);
  }

  @Override
  public boolean canInlineInstanceInitializer(
      IRCode code,
      InvokeDirect invoke,
      ProgramMethod singleTarget,
      InliningIRProvider inliningIRProvider,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return true;
  }

  @Override
  public boolean stillHasBudget(
      InlineAction action, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return true; // Unlimited allowance.
  }

  @Override
  public boolean willExceedBudget(
      IRCode code,
      InvokeMethod invoke,
      InlineeWithReason inlinee,
      BasicBlock block,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return false; // Unlimited allowance.
  }

  @Override
  public void markInlined(InlineeWithReason inlinee) {}

  @Override
  public ClassTypeElement getReceiverTypeOrDefault(
      InvokeMethod invoke, ClassTypeElement defaultValue) {
    assert invoke.isInvokeMethodWithReceiver();
    Inliner.InliningInfo info = invokesToInline.get(invoke.asInvokeMethodWithReceiver());
    assert info != null;
    if (info.receiverClass != null) {
      return info.receiverClass.getType().toTypeElement(appView).asClassType();
    }
    return defaultValue;
  }
}
