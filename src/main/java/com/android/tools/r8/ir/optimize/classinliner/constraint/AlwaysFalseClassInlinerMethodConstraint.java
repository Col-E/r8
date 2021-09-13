// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsage;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class AlwaysFalseClassInlinerMethodConstraint implements ClassInlinerMethodConstraint {

  private static final AlwaysFalseClassInlinerMethodConstraint INSTANCE =
      new AlwaysFalseClassInlinerMethodConstraint();

  private AlwaysFalseClassInlinerMethodConstraint() {}

  static AlwaysFalseClassInlinerMethodConstraint getInstance() {
    return INSTANCE;
  }

  @Override
  public ClassInlinerMethodConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection changes) {
    return this;
  }

  @Override
  public ParameterUsage getParameterUsage(int parameter) {
    return ParameterUsage.top();
  }

  @Override
  public boolean isEligibleForNewInstanceClassInlining(ProgramMethod method, int parameter) {
    return false;
  }

  @Override
  public boolean isEligibleForStaticGetClassInlining(
      AppView<AppInfoWithLiveness> appView,
      int parameter,
      ObjectState objectState,
      ProgramMethod context) {
    return false;
  }
}
