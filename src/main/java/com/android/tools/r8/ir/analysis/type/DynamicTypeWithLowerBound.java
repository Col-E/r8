// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

public class DynamicTypeWithLowerBound extends DynamicType {

  private final ClassTypeElement dynamicLowerBoundType;

  DynamicTypeWithLowerBound(
      ClassTypeElement dynamicUpperBoundType, ClassTypeElement dynamicLowerBoundType) {
    super(dynamicUpperBoundType);
    this.dynamicLowerBoundType = dynamicLowerBoundType;
  }

  public static DynamicTypeWithLowerBound create(
      AppView<AppInfoWithLiveness> appView,
      ClassTypeElement dynamicUpperBoundType,
      ClassTypeElement dynamicLowerBoundType) {
    assert appView
        .appInfo()
        .isSubtype(dynamicLowerBoundType.getClassType(), dynamicUpperBoundType.getClassType());
    return new DynamicTypeWithLowerBound(dynamicUpperBoundType, dynamicLowerBoundType);
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
  public boolean isTrivial(TypeElement staticType) {
    return false;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DynamicTypeWithLowerBound assumption = (DynamicTypeWithLowerBound) other;
    return getDynamicUpperBoundType() == assumption.getDynamicUpperBoundType()
        && getDynamicLowerBoundType() == assumption.getDynamicLowerBoundType();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDynamicUpperBoundType(), getDynamicLowerBoundType());
  }
}
