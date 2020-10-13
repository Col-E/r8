// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexDirectReferenceTracer;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.google.common.collect.Iterables;

public class DontInlinePolicy extends SingleClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;
  private final MainDexTracingResult mainDexTracingResult;

  public DontInlinePolicy(
      AppView<AppInfoWithLiveness> appView, MainDexTracingResult mainDexTracingResult) {
    this.appView = appView;
    this.mainDexTracingResult = mainDexTracingResult;
  }

  private boolean disallowInlining(ProgramMethod method) {
    Code code = method.getDefinition().getCode();

    // For non-jar/cf code we currently cannot guarantee that markForceInline() will succeed.
    if (code == null || !code.isCfCode()) {
      return true;
    }

    CfCode cfCode = code.asCfCode();

    ConstraintWithTarget constraint =
        cfCode.computeInliningConstraint(method, appView, appView.graphLens(), method);
    if (constraint == ConstraintWithTarget.NEVER) {
      return true;
    }

    // Constructors can have references beyond the root main dex classes. This can increase the
    // size of the main dex dependent classes and we should bail out.
    if (mainDexTracingResult.getRoots().contains(method.getHolderType())
        && MainDexDirectReferenceTracer.hasReferencesOutsideFromCode(
            appView.appInfo(), method, mainDexTracingResult.getRoots())) {
      return true;
    }

    return false;
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !Iterables.any(
        program.directProgramMethods(),
        method -> method.getDefinition().isInstanceInitializer() && disallowInlining(method));
  }
}
