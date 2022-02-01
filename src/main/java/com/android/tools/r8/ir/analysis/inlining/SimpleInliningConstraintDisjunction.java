// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;

public class SimpleInliningConstraintDisjunction extends SimpleInliningConstraint {

  private final List<SimpleInliningConstraint> constraints;

  public SimpleInliningConstraintDisjunction(List<SimpleInliningConstraint> constraints) {
    assert constraints.size() > 1;
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isAlways);
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isDisjunction);
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isNever);
    this.constraints = constraints;
  }

  SimpleInliningConstraint add(SimpleInliningConstraint constraint) {
    assert !constraint.isAlways();
    assert !constraint.isNever();
    if (constraint.isDisjunction()) {
      return addAll(constraint.asDisjunction());
    }
    assert constraint.isArgumentConstraint() || constraint.isConjunction();
    return new SimpleInliningConstraintDisjunction(
        ImmutableList.<SimpleInliningConstraint>builder()
            .addAll(constraints)
            .add(constraint)
            .build());
  }

  public SimpleInliningConstraintDisjunction addAll(
      SimpleInliningConstraintDisjunction disjunction) {
    return new SimpleInliningConstraintDisjunction(
        ImmutableList.<SimpleInliningConstraint>builder()
            .addAll(constraints)
            .addAll(disjunction.constraints)
            .build());
  }

  @Override
  public boolean isDisjunction() {
    return true;
  }

  @Override
  public SimpleInliningConstraintDisjunction asDisjunction() {
    return this;
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    for (SimpleInliningConstraint constraint : constraints) {
      if (constraint.isSatisfied(invoke)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public SimpleInliningConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView,
      ArgumentInfoCollection changes,
      SimpleInliningConstraintFactory factory) {
    List<SimpleInliningConstraint> rewrittenConstraints =
        ListUtils.mapOrElse(
            constraints,
            constraint -> {
              SimpleInliningConstraint rewrittenConstraint =
                  constraint.fixupAfterParametersChanged(appView, changes, factory);
              if (rewrittenConstraint.isNever()) {
                // Remove 'never' from disjunctions.
                return null;
              }
              return rewrittenConstraint;
            },
            null);

    if (rewrittenConstraints == null) {
      return this;
    }

    if (rewrittenConstraints.isEmpty()) {
      return NeverSimpleInliningConstraint.getInstance();
    }

    if (rewrittenConstraints.size() == 1) {
      return ListUtils.first(rewrittenConstraints);
    }

    if (Iterables.any(rewrittenConstraints, SimpleInliningConstraint::isAlways)) {
      return AlwaysSimpleInliningConstraint.getInstance();
    }

    return new SimpleInliningConstraintDisjunction(rewrittenConstraints);
  }
}
