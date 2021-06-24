// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.ints.IntList;

public abstract class NumberSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private final long rawValue;

  NumberSimpleInliningConstraint(int argumentIndex, long rawValue) {
    super(argumentIndex);
    this.rawValue = rawValue;
  }

  long getRawValue() {
    return rawValue;
  }

  @Override
  public final boolean isSatisfied(InvokeMethod invoke) {
    Value argumentRoot = getArgument(invoke).getAliasedValue();
    return argumentRoot.isDefinedByInstructionSatisfying(Instruction::isConstNumber)
        && test(argumentRoot.getDefinition().asConstNumber().getRawValue());
  }

  abstract boolean test(long argumentValue);

  @Override
  public final SimpleInliningConstraint rewrittenWithUnboxedArguments(
      IntList unboxedArgumentIndices, SimpleInliningConstraintFactory factory) {
    return this;
  }
}
