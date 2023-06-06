// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;

public class DefaultInliningReasonStrategy implements InliningReasonStrategy {

  private final AppView<AppInfoWithLiveness> appView;
  private final CallSiteInformation callSiteInformation;
  private final InlinerOptions options;

  public DefaultInliningReasonStrategy(
      AppView<AppInfoWithLiveness> appView, CallSiteInformation callSiteInformation) {
    this.appView = appView;
    this.callSiteInformation = callSiteInformation;
    this.options = appView.options().inlinerOptions();
  }

  @Override
  public Reason computeInliningReason(
      InvokeMethod invoke,
      ProgramMethod target,
      ProgramMethod context,
      DefaultInliningOracle oracle,
      MethodProcessor methodProcessor) {
    DexEncodedMethod targetMethod = target.getDefinition();
    DexMethod targetReference = target.getReference();
    if (targetMethod.getOptimizationInfo().forceInline()) {
      assert appView.getKeepInfo(target).isInliningAllowed(appView.options());
      return Reason.FORCE;
    }
    if (appView.appInfo().hasLiveness()
        && appView.withLiveness().appInfo().isAlwaysInlineMethod(targetReference)) {
      return Reason.ALWAYS;
    }
    if (options.disableInliningOfLibraryMethodOverrides
        && targetMethod.isLibraryMethodOverride().isTrue()) {
      // This method will always have an implicit call site from the library, so we won't be able to
      // remove it after inlining even if we have single or dual call site information from the
      // program.
      return Reason.SIMPLE;
    }
    if (isSingleCallerInliningTarget(context, target)) {
      return Reason.SINGLE_CALLER;
    }
    if (isMultiCallerInlineCandidate(invoke, target, oracle, methodProcessor)) {
      return methodProcessor.isPrimaryMethodProcessor()
          ? Reason.MULTI_CALLER_CANDIDATE
          : Reason.ALWAYS;
    }
    return Reason.SIMPLE;
  }

  private boolean isSingleCallerInliningTarget(ProgramMethod context, ProgramMethod method) {
    if (!callSiteInformation.hasSingleCallSite(context, method)) {
      return false;
    }
    if (appView.appInfo().isNeverInlineDueToSingleCallerMethod(method)) {
      return false;
    }
    if (appView.testing().validInliningReasons != null
        && !appView.testing().validInliningReasons.contains(Reason.SINGLE_CALLER)) {
      return false;
    }
    return true;
  }

  private boolean isMultiCallerInlineCandidate(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      DefaultInliningOracle oracle,
      MethodProcessor methodProcessor) {
    if (oracle.satisfiesRequirementsForSimpleInlining(invoke, singleTarget)) {
      return false;
    }
    if (methodProcessor.isPrimaryMethodProcessor()) {
      return callSiteInformation.isMultiCallerInlineCandidate(singleTarget);
    }
    if (methodProcessor.isPostMethodProcessor()) {
      return singleTarget.getOptimizationInfo().isMultiCallerMethod();
    }
    return false;
  }
}
