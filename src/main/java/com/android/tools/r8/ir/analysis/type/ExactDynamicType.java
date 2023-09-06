// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class ExactDynamicType extends DynamicTypeWithUpperBound {

  ExactDynamicType(ClassTypeElement exactDynamicType) {
    super(exactDynamicType);
  }

  @Override
  public ClassTypeElement getDynamicUpperBoundType() {
    return getExactClassType();
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return getExactClassType();
  }

  @Override
  public ClassTypeElement getExactClassType() {
    return super.getDynamicUpperBoundType().asClassType();
  }

  @Override
  public boolean hasDynamicLowerBoundType() {
    return true;
  }

  @Override
  public boolean isExactClassType() {
    return true;
  }

  @Override
  public DynamicType rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, Set<DexType> prunedTypes) {
    TypeElement rewrittenType =
        getExactClassType().rewrittenWithLens(appView, graphLens, null, prunedTypes);
    assert rewrittenType.isClassType() || rewrittenType.isPrimitiveType();
    return rewrittenType.isClassType()
        ? new ExactDynamicType(rewrittenType.asClassType())
        : unknown();
  }

  @Override
  public ExactDynamicType withNullability(Nullability nullability) {
    if (getNullability() == nullability) {
      return this;
    }
    return new ExactDynamicType(getExactClassType().getOrCreateVariant(nullability));
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ExactDynamicType dynamicType = (ExactDynamicType) other;
    return getExactClassType().equals(dynamicType.getExactClassType());
  }

  @Override
  public int hashCode() {
    return getExactClassType().hashCode();
  }

  @Override
  public String toString() {
    return "ExactDynamicType(type=" + getExactClassType() + ")";
  }
}
