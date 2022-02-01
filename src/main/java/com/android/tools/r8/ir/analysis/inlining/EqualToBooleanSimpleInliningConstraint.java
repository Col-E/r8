// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** Constraint that is satisfied if a specific argument is always true. */
public class EqualToBooleanSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private final boolean value;

  private EqualToBooleanSimpleInliningConstraint(int argumentIndex, boolean value) {
    super(argumentIndex);
    this.value = value;
  }

  static EqualToBooleanSimpleInliningConstraint create(
      int argumentIndex, boolean value, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new EqualToBooleanSimpleInliningConstraint(argumentIndex, value);
  }

  @Override
  public SimpleInliningConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView,
      ArgumentInfoCollection changes,
      SimpleInliningConstraintFactory factory) {
    if (changes.isArgumentRemoved(getArgumentIndex())) {
      RemovedArgumentInfo removedArgumentInfo =
          changes.getArgumentInfo(getArgumentIndex()).asRemovedArgumentInfo();
      if (!removedArgumentInfo.hasSingleValue()) {
        // We should never have constraints for unused arguments.
        assert false;
        return NeverSimpleInliningConstraint.getInstance();
      }
      SingleValue singleValue = removedArgumentInfo.getSingleValue();
      return singleValue.isSingleNumberValue()
              && singleValue.asSingleNumberValue().getBooleanValue() == value
          ? AlwaysSimpleInliningConstraint.getInstance()
          : NeverSimpleInliningConstraint.getInstance();
    } else {
      assert !changes.hasArgumentInfo(getArgumentIndex());
    }
    return withArgumentIndex(changes.getNewArgumentIndex(getArgumentIndex()), factory);
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    Value argumentRoot = getArgument(invoke).getAliasedValue();
    return argumentRoot.isDefinedByInstructionSatisfying(Instruction::isConstNumber)
        && argumentRoot.getDefinition().asConstNumber().getBooleanValue() == value;
  }

  @Override
  SimpleInliningArgumentConstraint withArgumentIndex(
      int argumentIndex, SimpleInliningConstraintFactory factory) {
    return factory.createEqualToBooleanConstraint(argumentIndex, value);
  }
}
