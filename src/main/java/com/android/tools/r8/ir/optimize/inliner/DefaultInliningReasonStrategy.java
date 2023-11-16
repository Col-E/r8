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
import java.util.Set;

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
      InliningIRProvider inliningIRProvider,
      MethodProcessor methodProcessor,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    DexEncodedMethod targetMethod = target.getDefinition();
    DexMethod targetReference = target.getReference();
    Reason reason;
    if (appView.appInfo().hasLiveness()
        && appView.withLiveness().appInfo().isAlwaysInlineMethod(targetReference)) {
      reason = Reason.ALWAYS;
    } else if (options.disableInliningOfLibraryMethodOverrides
        && targetMethod.isLibraryMethodOverride().isTrue()) {
      // This method will always have an implicit call site from the library, so we won't be able to
      // remove it after inlining even if we have single or dual call site information from the
      // program.
      reason = Reason.SIMPLE;
    } else if (callSiteInformation.hasSingleCallSite(target, context)) {
      reason = Reason.SINGLE_CALLER;
    } else if (isMultiCallerInlineCandidate(target, methodProcessor)) {
      reason =
          methodProcessor.isPrimaryMethodProcessor()
              ? Reason.MULTI_CALLER_CANDIDATE
              : Reason.ALWAYS;
    } else {
      reason = Reason.SIMPLE;
    }
    Set<Reason> validInliningReasons = appView.testing().validInliningReasons;
    if (validInliningReasons != null && !validInliningReasons.contains(reason)) {
      reason = Reason.NEVER;
      whyAreYouNotInliningReporter.reportInvalidInliningReason(reason, validInliningReasons);
    }
    return reason;
  }

  private boolean isMultiCallerInlineCandidate(
      ProgramMethod singleTarget,
      MethodProcessor methodProcessor) {
    if (methodProcessor.isPrimaryMethodProcessor()) {
      return callSiteInformation.isMultiCallerInlineCandidate(singleTarget);
    }
    if (methodProcessor.isPostMethodProcessor()) {
      return singleTarget.getOptimizationInfo().isMultiCallerMethod();
    }
    return false;
  }
}
