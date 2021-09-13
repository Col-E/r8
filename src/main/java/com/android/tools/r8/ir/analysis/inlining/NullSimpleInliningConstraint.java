// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfo;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** Constraint that is satisfied if a specific argument is always null. */
public class NullSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private final Nullability nullability;

  private NullSimpleInliningConstraint(int argumentIndex, Nullability nullability) {
    super(argumentIndex);
    assert nullability.isDefinitelyNull() || nullability.isDefinitelyNotNull();
    this.nullability = nullability;
  }

  static NullSimpleInliningConstraint create(
      int argumentIndex, Nullability nullability, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new NullSimpleInliningConstraint(argumentIndex, nullability);
  }

  @Override
  public SimpleInliningConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView,
      ArgumentInfoCollection changes,
      SimpleInliningConstraintFactory factory) {
    ArgumentInfo argumentInfo = changes.getArgumentInfo(getArgumentIndex());
    if (argumentInfo.isRemovedArgumentInfo()) {
      RemovedArgumentInfo removedArgumentInfo =
          changes.getArgumentInfo(getArgumentIndex()).asRemovedArgumentInfo();
      if (!removedArgumentInfo.hasSingleValue()) {
        // We should never have constraints for unused arguments.
        assert false;
        return NeverSimpleInliningConstraint.getInstance();
      }
      return removedArgumentInfo.getSingleValue().isNull() && nullability.isDefinitelyNull()
          ? AlwaysSimpleInliningConstraint.getInstance()
          : NeverSimpleInliningConstraint.getInstance();
    } else if (argumentInfo.isRewrittenTypeInfo()) {
      RewrittenTypeInfo rewrittenTypeInfo = argumentInfo.asRewrittenTypeInfo();
      // We should only get here as a result of enum unboxing.
      assert rewrittenTypeInfo.verifyIsDueToUnboxing(appView.dexItemFactory());
      // Rewrite definitely-null constraints to definitely-zero constraints.
      return nullability.isDefinitelyNull()
          ? factory.createEqualToNumberConstraint(getArgumentIndex(), 0)
          : factory.createNotEqualToNumberConstraint(getArgumentIndex(), 0);
    }
    return withArgumentIndex(changes.getNewArgumentIndex(getArgumentIndex()), factory);
  }

  @Override
  public final boolean isSatisfied(InvokeMethod invoke) {
    Value argument = getArgument(invoke);
    TypeElement argumentType = argument.getType();
    assert argumentType.isReferenceType();

    if (argumentType.nullability() == nullability) {
      return true;
    }

    // Take the root value to also deal with the following case, which may happen in dead code,
    // where v1 is actually guaranteed to be null, despite the value's type being non-null:
    //   v0 <- ConstNumber 0
    //   v1 <- AssumeNotNull v0
    return argument.isDefinedByInstructionSatisfying(Instruction::isAssume)
        && argument.getAliasedValue().getType().nullability() == nullability;
  }

  @Override
  SimpleInliningArgumentConstraint withArgumentIndex(
      int argumentIndex, SimpleInliningConstraintFactory factory) {
    return factory.createNullConstraint(argumentIndex, nullability);
  }
}
