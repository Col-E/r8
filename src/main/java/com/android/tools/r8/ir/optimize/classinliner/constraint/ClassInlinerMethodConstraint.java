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

public interface ClassInlinerMethodConstraint {

  ClassInlinerMethodConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection changes);

  ParameterUsage getParameterUsage(int parameter);

  boolean isEligibleForNewInstanceClassInlining(ProgramMethod method, int parameter);

  boolean isEligibleForStaticGetClassInlining(
      AppView<AppInfoWithLiveness> appView,
      int parameter,
      ObjectState objectState,
      ProgramMethod context);

  static AlwaysFalseClassInlinerMethodConstraint alwaysFalse() {
    return AlwaysFalseClassInlinerMethodConstraint.getInstance();
  }

  static AlwaysTrueClassInlinerMethodConstraint alwaysTrue() {
    return AlwaysTrueClassInlinerMethodConstraint.getInstance();
  }
}
