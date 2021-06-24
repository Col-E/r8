// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

public class NotEqualToNumberSimpleInliningConstraint extends NumberSimpleInliningConstraint {

  private NotEqualToNumberSimpleInliningConstraint(int argumentIndex, long rawValue) {
    super(argumentIndex, rawValue);
  }

  static NotEqualToNumberSimpleInliningConstraint create(
      int argumentIndex, long rawValue, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new NotEqualToNumberSimpleInliningConstraint(argumentIndex, rawValue);
  }

  @Override
  boolean test(long argumentValue) {
    return argumentValue != getRawValue();
  }

  @Override
  SimpleInliningArgumentConstraint withArgumentIndex(
      int argumentIndex, SimpleInliningConstraintFactory factory) {
    return factory.createNotEqualToNumberConstraint(argumentIndex, getRawValue());
  }
}
