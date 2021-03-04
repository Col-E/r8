// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsage;

public class AlwaysTrueClassInlinerMethodConstraint implements ClassInlinerMethodConstraint {

  private static final AlwaysTrueClassInlinerMethodConstraint INSTANCE =
      new AlwaysTrueClassInlinerMethodConstraint();

  private AlwaysTrueClassInlinerMethodConstraint() {}

  static AlwaysTrueClassInlinerMethodConstraint getInstance() {
    return INSTANCE;
  }

  @Override
  public ClassInlinerMethodConstraint fixupAfterRemovingThisParameter() {
    return this;
  }

  @Override
  public ParameterUsage getParameterUsage(int parameter) {
    return ParameterUsage.bottom();
  }

  @Override
  public boolean isEligibleForNewInstanceClassInlining(ProgramMethod method, int parameter) {
    return true;
  }

  @Override
  public boolean isEligibleForStaticGetClassInlining(ProgramMethod method, int parameter) {
    return true;
  }
}
