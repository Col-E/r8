// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.ints.IntList;

/** Constraint that is satisfied if a specific argument is always true. */
public class BooleanTrueSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private BooleanTrueSimpleInliningConstraint(int argumentIndex) {
    super(argumentIndex);
  }

  static BooleanTrueSimpleInliningConstraint create(
      int argumentIndex, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new BooleanTrueSimpleInliningConstraint(argumentIndex);
  }

  @Override
  public boolean isBooleanTrue() {
    return true;
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    Value argument = getArgument(invoke);
    assert argument.getType().isInt();
    return argument.isConstBoolean(true);
  }

  @Override
  public SimpleInliningConstraint fixupAfterRemovingThisParameter(
      SimpleInliningConstraintFactory factory) {
    assert getArgumentIndex() > 0;
    return factory.createBooleanTrueConstraint(getArgumentIndex() - 1);
  }

  @Override
  public SimpleInliningConstraint rewrittenWithUnboxedArguments(IntList unboxedArgumentIndices) {
    return this;
  }
}
