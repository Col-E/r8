// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.ints.IntList;

public class EqualToNumberSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private final long rawValue;

  private EqualToNumberSimpleInliningConstraint(int argumentIndex, long rawValue) {
    super(argumentIndex);
    this.rawValue = rawValue;
  }

  static EqualToNumberSimpleInliningConstraint create(
      int argumentIndex, long rawValue, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new EqualToNumberSimpleInliningConstraint(argumentIndex, rawValue);
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    Value argumentRoot = getArgument(invoke).getAliasedValue();
    return argumentRoot.isDefinedByInstructionSatisfying(Instruction::isConstNumber)
        && argumentRoot.getDefinition().asConstNumber().getRawValue() == rawValue;
  }

  @Override
  public SimpleInliningConstraint fixupAfterRemovingThisParameter(
      SimpleInliningConstraintFactory factory) {
    assert getArgumentIndex() > 0;
    return factory.createNumberConstraint(getArgumentIndex() - 1, rawValue);
  }

  @Override
  public SimpleInliningConstraint rewrittenWithUnboxedArguments(
      IntList unboxedArgumentIndices, SimpleInliningConstraintFactory factory) {
    return this;
  }
}
