// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.InvokeMethod;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.function.Supplier;

public abstract class SimpleInliningConstraint {

  public boolean isAlways() {
    return false;
  }

  public boolean isArgumentConstraint() {
    return false;
  }

  public boolean isConjunction() {
    return false;
  }

  public SimpleInliningConstraintConjunction asConjunction() {
    return null;
  }

  public boolean isDisjunction() {
    return false;
  }

  public SimpleInliningConstraintDisjunction asDisjunction() {
    return null;
  }

  public boolean isNever() {
    return false;
  }

  public abstract boolean isSatisfied(InvokeMethod invoke);

  public final SimpleInliningConstraint meet(SimpleInliningConstraint other) {
    if (isAlways()) {
      return other;
    }
    if (other.isAlways()) {
      return this;
    }
    if (isNever() || other.isNever()) {
      return NeverSimpleInliningConstraint.getInstance();
    }
    if (isConjunction()) {
      return asConjunction().add(other);
    }
    if (other.isConjunction()) {
      return other.asConjunction().add(this);
    }
    assert isArgumentConstraint() || isDisjunction();
    assert other.isArgumentConstraint() || other.isDisjunction();
    return new SimpleInliningConstraintConjunction(ImmutableList.of(this, other));
  }

  public final SimpleInliningConstraint lazyMeet(Supplier<SimpleInliningConstraint> supplier) {
    if (isNever()) {
      return NeverSimpleInliningConstraint.getInstance();
    }
    return meet(supplier.get());
  }

  public final SimpleInliningConstraint join(SimpleInliningConstraint other) {
    if (isAlways() || other.isAlways()) {
      return AlwaysSimpleInliningConstraint.getInstance();
    }
    if (isNever()) {
      return other;
    }
    if (other.isNever()) {
      return this;
    }
    if (isDisjunction()) {
      return asDisjunction().add(other);
    }
    if (other.isDisjunction()) {
      return other.asDisjunction().add(this);
    }
    assert isArgumentConstraint() || isConjunction();
    assert other.isArgumentConstraint() || other.isConjunction();
    return new SimpleInliningConstraintDisjunction(ImmutableList.of(this, other));
  }

  public abstract SimpleInliningConstraint fixupAfterRemovingThisParameter(
      SimpleInliningConstraintFactory factory);

  public abstract SimpleInliningConstraint rewrittenWithUnboxedArguments(
      IntList unboxedArgumentIndices, SimpleInliningConstraintFactory factory);
}
