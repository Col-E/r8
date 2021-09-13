// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;

public abstract class SimpleInliningArgumentConstraint extends SimpleInliningConstraint {

  private final int argumentIndex;

  SimpleInliningArgumentConstraint(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  Value getArgument(InvokeMethod invoke) {
    return invoke.getArgument(argumentIndex);
  }

  int getArgumentIndex() {
    return argumentIndex;
  }

  @Override
  public boolean isArgumentConstraint() {
    return true;
  }

  abstract SimpleInliningArgumentConstraint withArgumentIndex(
      int argumentIndex, SimpleInliningConstraintFactory factory);
}
