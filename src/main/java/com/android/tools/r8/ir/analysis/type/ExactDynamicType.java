// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

public class ExactDynamicType extends DynamicType {

  ExactDynamicType(ClassTypeElement exactDynamicType) {
    super(exactDynamicType);
  }

  @Override
  public boolean hasDynamicLowerBoundType() {
    return true;
  }

  @Override
  public ClassTypeElement getDynamicUpperBoundType() {
    return super.getDynamicUpperBoundType().asClassType();
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return getDynamicUpperBoundType();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ExactDynamicType dynamicType = (ExactDynamicType) other;
    return getDynamicUpperBoundType().equals(dynamicType.getDynamicUpperBoundType());
  }

  @Override
  public int hashCode() {
    return getDynamicLowerBoundType().hashCode();
  }

  @Override
  public DynamicType withNullability(Nullability nullability) {
    if (getDynamicUpperBoundType().nullability() == nullability) {
      return this;
    }
    return new ExactDynamicType(getDynamicUpperBoundType().getOrCreateVariant(nullability));
  }
}
