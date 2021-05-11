// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;

public class NoIllegalInlining extends SingleClassPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoIllegalInlining(AppView<AppInfoWithLiveness> appView, Mode mode) {
    // This policy is only relevant for the first round of horizontal class merging, since the final
    // round of horizontal class merging may not require any inlining.
    assert mode.isInitial();
    this.appView = appView;
  }

  private boolean disallowInlining(ProgramMethod method) {
    Code code = method.getDefinition().getCode();

    if (appView.appInfo().isNeverInlineMethod(method.getReference())) {
      return true;
    }

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

    return false;
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !Iterables.any(
        program.directProgramMethods(),
        method -> method.getDefinition().isInstanceInitializer() && disallowInlining(method));
  }

  @Override
  public String getName() {
    return "DontInlinePolicy";
  }
}
