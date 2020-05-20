// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineeWithReason;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;

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
  public boolean isForcedInliningOracle() {
    return true;
  }

  @Override
  public boolean passesInliningConstraints(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod candidate,
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
  public InlineAction computeInlining(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod singleTarget,
      ProgramMethod context,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return computeForInvoke(invoke, resolutionResult, whyAreYouNotInliningReporter);
  }

  private InlineAction computeForInvoke(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    Inliner.InliningInfo info = invokesToInline.get(invoke);
    if (info == null) {
      return null;
    }

    assert method.getDefinition() != info.target.getDefinition();
    // Even though call to Inliner::performForcedInlining is supposed to be controlled by
    // the caller, it's still suspicious if we want to force inline something that is marked
    // with neverInline() flag.
    assert !info.target.getDefinition().getOptimizationInfo().neverInline();
    assert passesInliningConstraints(
        invoke, resolutionResult, info.target, Reason.FORCE, whyAreYouNotInliningReporter);
    return new InlineAction(info.target, invoke, Reason.FORCE);
  }

  @Override
  public void ensureMethodProcessed(
      ProgramMethod target, IRCode inlinee, OptimizationFeedback feedback) {
    // Do nothing. If the method is not yet processed, we still should
    // be able to build IR for inlining, though.
  }

  @Override
  public boolean allowInliningOfInvokeInInlinee(
      InlineAction action,
      int inliningDepth,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    // The purpose of force inlining is generally to inline a given invoke-instruction in the IR.
    return false;
  }

  @Override
  public boolean canInlineInstanceInitializer(
      IRCode code, WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
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
  public DexType getReceiverTypeIfKnown(InvokeMethod invoke) {
    assert invoke.isInvokeMethodWithReceiver();
    Inliner.InliningInfo info = invokesToInline.get(invoke.asInvokeMethodWithReceiver());
    assert info != null;
    return info.receiverType;
  }
}
