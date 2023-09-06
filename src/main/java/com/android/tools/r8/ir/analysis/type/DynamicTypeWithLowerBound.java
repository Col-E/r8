// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

public class DynamicTypeWithLowerBound extends DynamicTypeWithUpperBound {

  private final ClassTypeElement dynamicLowerBoundType;

  private DynamicTypeWithLowerBound(
      ClassTypeElement dynamicUpperBoundType, ClassTypeElement dynamicLowerBoundType) {
    super(dynamicUpperBoundType);
    assert !dynamicUpperBoundType.equals(dynamicLowerBoundType);
    assert dynamicUpperBoundType.nullability() == dynamicLowerBoundType.nullability();
    this.dynamicLowerBoundType = dynamicLowerBoundType;
  }

  static DynamicTypeWithLowerBound create(
      AppView<AppInfoWithLiveness> appView,
      ClassTypeElement dynamicUpperBoundType,
      ClassTypeElement dynamicLowerBoundType) {
    assert dynamicUpperBoundType != null;
    assert dynamicLowerBoundType != null;
    assert dynamicUpperBoundType.nullability() == dynamicLowerBoundType.nullability();
    assert appView
        .appInfo()
        .isStrictSubtypeOf(
            dynamicLowerBoundType.getClassType(), dynamicUpperBoundType.getClassType());
    return new DynamicTypeWithLowerBound(dynamicUpperBoundType, dynamicLowerBoundType);
  }

  @Override
  public ClassTypeElement getDynamicUpperBoundType() {
    return super.getDynamicUpperBoundType().asClassType();
  }

  @Override
  public boolean hasDynamicLowerBoundType() {
    return true;
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return dynamicLowerBoundType;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DynamicTypeWithLowerBound dynamicType = (DynamicTypeWithLowerBound) other;
    return getDynamicUpperBoundType().equals(dynamicType.getDynamicUpperBoundType())
        && getDynamicLowerBoundType().equals(dynamicType.getDynamicLowerBoundType());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDynamicUpperBoundType(), getDynamicLowerBoundType());
  }

  @Override
  public String toString() {
    return "DynamicTypeWithLowerBound(upperBound="
        + getDynamicUpperBoundType()
        + ", lowerBound="
        + getDynamicLowerBoundType()
        + ")";
  }

  @Override
  public DynamicTypeWithLowerBound withNullability(Nullability nullability) {
    if (getDynamicUpperBoundType().nullability() == nullability) {
      return this;
    }
    return new DynamicTypeWithLowerBound(
        getDynamicUpperBoundType().getOrCreateVariant(nullability),
        getDynamicLowerBoundType().getOrCreateVariant(nullability));
  }
}
