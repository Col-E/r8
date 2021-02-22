// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.graph.ProgramMethod;

public class AlwaysFalseClassInlinerMethodConstraint implements ClassInlinerMethodConstraint {

  private static final AlwaysFalseClassInlinerMethodConstraint INSTANCE =
      new AlwaysFalseClassInlinerMethodConstraint();

  private AlwaysFalseClassInlinerMethodConstraint() {}

  public static AlwaysFalseClassInlinerMethodConstraint getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isEligibleForNewInstanceClassInlining(ProgramMethod method) {
    return false;
  }

  @Override
  public boolean isEligibleForStaticGetClassInlining(ProgramMethod method) {
    return false;
  }
}
