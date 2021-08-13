// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * Represents the runtime type of a reference value. This type may be more precise than the value's
 * statically declared type.
 *
 * <p>If a lower bound is known on the runtime type (e.g., {@code new A()}), then {@link
 * DynamicTypeWithLowerBound} is used.
 */
public class DynamicType {

  private static final DynamicType BOTTOM = new DynamicType(TypeElement.getBottom());
  private static final DynamicType UNKNOWN = new DynamicType(TypeElement.getTop());

  private final TypeElement dynamicUpperBoundType;

  DynamicType(TypeElement dynamicUpperBoundType) {
    assert dynamicUpperBoundType != null;
    this.dynamicUpperBoundType = dynamicUpperBoundType;
  }

  public static DynamicType create(Value value, AppView<AppInfoWithLiveness> appView) {
    assert value.getType().isReferenceType();
    TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
    ClassTypeElement dynamicLowerBoundType = value.getDynamicLowerBoundType(appView);
    if (dynamicLowerBoundType != null) {
      assert dynamicUpperBoundType.isClassType();
      return DynamicTypeWithLowerBound.create(
          appView, dynamicUpperBoundType.asClassType(), dynamicLowerBoundType);
    }
    return new DynamicType(dynamicUpperBoundType);
  }

  public static DynamicType bottom() {
    return BOTTOM;
  }

  public static DynamicType unknown() {
    return UNKNOWN;
  }

  public TypeElement getDynamicUpperBoundType() {
    return dynamicUpperBoundType;
  }

  public boolean hasDynamicLowerBoundType() {
    return false;
  }

  public ClassTypeElement getDynamicLowerBoundType() {
    return null;
  }

  public boolean isTrivial(TypeElement staticType) {
    return staticType == getDynamicUpperBoundType() || isUnknown();
  }

  public boolean isUnknown() {
    return getDynamicUpperBoundType().isTop();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DynamicType assumption = (DynamicType) other;
    return dynamicUpperBoundType == assumption.dynamicUpperBoundType;
  }

  @Override
  public int hashCode() {
    return dynamicUpperBoundType.hashCode();
  }
}
