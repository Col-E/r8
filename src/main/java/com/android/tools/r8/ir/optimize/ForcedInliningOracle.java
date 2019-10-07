// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineeWithReason;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ListIterator;
import java.util.Map;

final class ForcedInliningOracle implements InliningOracle, InliningStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexEncodedMethod method;
  private final Map<InvokeMethod, Inliner.InliningInfo> invokesToInline;

  ForcedInliningOracle(
      AppView<AppInfoWithLiveness> appView,
      DexEncodedMethod method,
      Map<InvokeMethod, Inliner.InliningInfo> invokesToInline) {
    this.appView = appView;
    this.method = method;
    this.invokesToInline = invokesToInline;
  }

  @Override
  public boolean isForcedInliningOracle() {
    return true;
  }

  @Override
  public void finish() {}

  @Override
  public DexEncodedMethod lookupSingleTarget(InvokeMethod invoke, DexType context) {
    Inliner.InliningInfo info = invokesToInline.get(invoke);
    if (info != null) {
      return info.target;
    }
    return invoke.lookupSingleTarget(appView, context);
  }

  @Override
  public InlineAction computeForInvokeWithReceiver(
      InvokeMethodWithReceiver invoke,
      DexEncodedMethod singleTarget,
      DexMethod invocationContext,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return computeForInvoke(invoke);
  }

  @Override
  public InlineAction computeForInvokeStatic(
      InvokeStatic invoke,
      DexEncodedMethod singleTarget,
      DexMethod invocationContext,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return computeForInvoke(invoke);
  }

  private InlineAction computeForInvoke(InvokeMethod invoke) {
    Inliner.InliningInfo info = invokesToInline.get(invoke);
    if (info == null) {
      return null;
    }

    assert method != info.target;
    // Even though call to Inliner::performForcedInlining is supposed to be controlled by
    // the caller, it's still suspicious if we want to force inline something that is marked
    // with neverInline() flag.
    assert !info.target.getOptimizationInfo().neverInline();
    return new InlineAction(info.target, invoke, Reason.FORCE);
  }

  @Override
  public void ensureMethodProcessed(
      DexEncodedMethod target, IRCode inlinee, OptimizationFeedback feedback) {
    // Do nothing. If the method is not yet processed, we still should
    // be able to build IR for inlining, though.
  }

  @Override
  public void updateTypeInformationIfNeeded(
      IRCode inlinee, ListIterator<BasicBlock> blockIterator, BasicBlock block) {}

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
