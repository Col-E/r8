// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;

/** Constraint that is satisfied if a specific argument is always null. */
public class NullSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private NullSimpleInliningConstraint(int argumentIndex) {
    super(argumentIndex);
  }

  static NullSimpleInliningConstraint create(
      int argumentIndex, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new NullSimpleInliningConstraint(argumentIndex);
  }

  @Override
  public boolean isNull() {
    return true;
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    Value argument = getArgument(invoke);
    assert argument.getType().isReferenceType();
    return argument.getType().isDefinitelyNull();
  }
}
