// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.optimize.compose.ArgumentPropagatorComposeModeling;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ArgumentPropagatorCodeScannerModeling {

  private final ArgumentPropagatorComposeModeling composeModeling;

  ArgumentPropagatorCodeScannerModeling(AppView<AppInfoWithLiveness> appView) {
    this.composeModeling =
        appView.testing().modelUnknownChangedAndDefaultArgumentsToComposableFunctions
            ? new ArgumentPropagatorComposeModeling(appView)
            : null;
  }

  ParameterState modelParameterStateForArgumentToFunction(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      int argumentIndex,
      Value argument,
      ProgramMethod context) {
    if (composeModeling != null) {
      return composeModeling.modelParameterStateForChangedOrDefaultArgumentToComposableFunction(
          invoke, singleTarget, argumentIndex, argument, context);
    }
    return null;
  }
}
