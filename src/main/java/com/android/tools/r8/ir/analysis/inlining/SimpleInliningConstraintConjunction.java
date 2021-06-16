// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;

public class SimpleInliningConstraintConjunction extends SimpleInliningConstraint {

  private final List<SimpleInliningConstraint> constraints;

  public SimpleInliningConstraintConjunction(List<SimpleInliningConstraint> constraints) {
    assert constraints.size() > 1;
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isAlways);
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isConjunction);
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isNever);
    this.constraints = constraints;
  }

  SimpleInliningConstraint add(SimpleInliningConstraint constraint) {
    assert !constraint.isAlways();
    assert !constraint.isNever();
    if (constraint.isConjunction()) {
      return addAll(constraint.asConjunction());
    }
    assert constraint.isArgumentConstraint() || constraint.isDisjunction();
    return new SimpleInliningConstraintConjunction(
        ImmutableList.<SimpleInliningConstraint>builder()
            .addAll(constraints)
            .add(constraint)
            .build());
  }

  public SimpleInliningConstraintConjunction addAll(
      SimpleInliningConstraintConjunction conjunction) {
    return new SimpleInliningConstraintConjunction(
        ImmutableList.<SimpleInliningConstraint>builder()
            .addAll(constraints)
            .addAll(conjunction.constraints)
            .build());
  }

  @Override
  public boolean isConjunction() {
    return true;
  }

  @Override
  public SimpleInliningConstraintConjunction asConjunction() {
    return this;
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    for (SimpleInliningConstraint constraint : constraints) {
      if (!constraint.isSatisfied(invoke)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public SimpleInliningConstraint fixupAfterRemovingThisParameter(
      SimpleInliningConstraintFactory factory) {
    List<SimpleInliningConstraint> rewrittenConstraints =
        ListUtils.mapOrElse(
            constraints, constraint -> constraint.fixupAfterRemovingThisParameter(factory), null);
    return rewrittenConstraints != null
        ? new SimpleInliningConstraintConjunction(rewrittenConstraints)
        : this;
  }

  @Override
  public SimpleInliningConstraint rewrittenWithUnboxedArguments(IntList unboxedArgumentIndices) {
    List<SimpleInliningConstraint> rewrittenConstraints =
        ListUtils.mapOrElse(
            constraints,
            constraint -> constraint.rewrittenWithUnboxedArguments(unboxedArgumentIndices),
            null);
    return rewrittenConstraints != null
        ? new SimpleInliningConstraintConjunction(rewrittenConstraints)
        : this;
  }
}
