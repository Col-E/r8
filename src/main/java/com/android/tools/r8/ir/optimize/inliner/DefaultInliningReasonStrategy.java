// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.MethodProcessor;
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
      MethodProcessor methodProcessor) {
    DexEncodedMethod targetMethod = target.getDefinition();
    DexMethod targetReference = target.getReference();
    if (targetMethod.getOptimizationInfo().forceInline()) {
      assert !appView.appInfo().isNeverInlineMethod(targetReference);
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
    if (isSingleCallerInliningTarget(target)) {
      return Reason.SINGLE_CALLER;
    }
    if (isDoubleInliningTarget(target)) {
      assert methodProcessor.isPrimaryMethodProcessor();
      return Reason.DUAL_CALLER;
    }
    return Reason.SIMPLE;
  }

  private boolean isSingleCallerInliningTarget(ProgramMethod method) {
    if (!callSiteInformation.hasSingleCallSite(method)) {
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

  private boolean isDoubleInliningTarget(ProgramMethod candidate) {
    return callSiteInformation.hasDoubleCallSite(candidate)
        && candidate
            .getDefinition()
            .getCode()
            .estimatedSizeForInliningAtMost(options.getDoubleInliningInstructionLimit());
  }
}
