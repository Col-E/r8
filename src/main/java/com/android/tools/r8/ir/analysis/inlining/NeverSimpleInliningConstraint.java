// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.ir.code.InvokeMethod;
import it.unimi.dsi.fastutil.ints.IntList;

/** Constraint that is never satisfied. */
public class NeverSimpleInliningConstraint extends SimpleInliningConstraint {

  public static final NeverSimpleInliningConstraint INSTANCE = new NeverSimpleInliningConstraint();

  private NeverSimpleInliningConstraint() {}

  public static NeverSimpleInliningConstraint getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isNever() {
    return true;
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    return false;
  }

  @Override
  public SimpleInliningConstraint fixupAfterRemovingThisParameter(
      SimpleInliningConstraintFactory factory) {
    return this;
  }

  @Override
  public SimpleInliningConstraint rewrittenWithUnboxedArguments(
      IntList unboxedArgumentIndices, SimpleInliningConstraintFactory factory) {
    return this;
  }
}
