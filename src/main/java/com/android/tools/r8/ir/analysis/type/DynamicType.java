// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

/**
 * Represents the runtime type of a reference value. This type may be more precise than the value's
 * statically declared type.
 *
 * <p>If a lower bound is known on the runtime type (e.g., {@code new A()}), then {@link
 * DynamicTypeWithLowerBound} is used.
 */
public abstract class DynamicType {

  public static DynamicTypeWithUpperBound create(
      AppView<AppInfoWithLiveness> appView, TypeElement dynamicUpperBoundType) {
    ClassTypeElement dynamicLowerBoundType = null;
    if (dynamicUpperBoundType.isClassType()) {
      ClassTypeElement dynamicUpperBoundClassType = dynamicUpperBoundType.asClassType();
      DexClass dynamicUpperBoundClass =
          appView.definitionFor(dynamicUpperBoundClassType.getClassType());
      if (dynamicUpperBoundClass != null && dynamicUpperBoundClass.isEffectivelyFinal(appView)) {
        dynamicLowerBoundType = dynamicUpperBoundClassType;
      }
    }
    return create(appView, dynamicUpperBoundType, dynamicLowerBoundType);
  }

  public static DynamicTypeWithUpperBound create(
      AppView<AppInfoWithLiveness> appView,
      TypeElement dynamicUpperBoundType,
      ClassTypeElement dynamicLowerBoundType) {
    if (dynamicUpperBoundType.isBottom()) {
      return bottom();
    }
    if (dynamicUpperBoundType.isNullType()) {
      return definitelyNull();
    }
    if (dynamicUpperBoundType.isTop()) {
      return unknown();
    }
    if (dynamicLowerBoundType != null) {
      assert dynamicUpperBoundType.isClassType();
      assert dynamicUpperBoundType.nullability() == dynamicLowerBoundType.nullability();
      if (dynamicUpperBoundType.equals(dynamicLowerBoundType)) {
        return createExact(dynamicLowerBoundType);
      }
      return DynamicTypeWithLowerBound.create(
          appView, dynamicUpperBoundType.asClassType(), dynamicLowerBoundType);
    }
    assert verifyNotEffectivelyFinalClassType(appView, dynamicUpperBoundType);
    return new DynamicTypeWithUpperBound(dynamicUpperBoundType);
  }

  public static ExactDynamicType createExact(ClassTypeElement exactDynamicType) {
    return new ExactDynamicType(exactDynamicType);
  }

  public static DynamicTypeWithUpperBound create(
      AppView<AppInfoWithLiveness> appView, Value value) {
    assert value.getType().isReferenceType();
    TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
    ClassTypeElement dynamicLowerBoundType =
        value.getDynamicLowerBoundType(
            appView, dynamicUpperBoundType, dynamicUpperBoundType.nullability());
    return create(appView, dynamicUpperBoundType, dynamicLowerBoundType);
  }

  public static DynamicTypeWithUpperBound bottom() {
    return DynamicTypeWithUpperBound.BOTTOM;
  }

  public static DynamicTypeWithUpperBound definitelyNull() {
    return DynamicTypeWithUpperBound.NULL_TYPE;
  }

  public static NotNullDynamicType definitelyNotNull() {
    return NotNullDynamicType.get();
  }

  public static DynamicTypeWithUpperBound unknown() {
    return DynamicTypeWithUpperBound.UNKNOWN;
  }

  public static DynamicType join(
      AppView<AppInfoWithLiveness> appView, Iterable<DynamicType> dynamicTypes) {
    DynamicType result = bottom();
    for (DynamicType dynamicType : dynamicTypes) {
      result = result.join(appView, dynamicType);
    }
    return result;
  }

  public boolean hasDynamicUpperBoundType() {
    return false;
  }

  /**
   * Returns the dynamic upper bound type if this is an instance of {@link
   * DynamicTypeWithUpperBound}.
   *
   * <p>The {@link NotNullDynamicType} does not have an upper bound type. This therefore takes the
   * static type corresponding to the dynamic type as an argument, and returns the given static type
   * with non null information attached to it when this dynamic type is the {@link
   * NotNullDynamicType}.
   */
  public abstract TypeElement getDynamicUpperBoundType(TypeElement staticType);

  public boolean hasDynamicLowerBoundType() {
    return false;
  }

  public ClassTypeElement getDynamicLowerBoundType() {
    return null;
  }

  public abstract ClassTypeElement getExactClassType();

  public abstract Nullability getNullability();

  public boolean isBottom() {
    return false;
  }

  public boolean isDynamicTypeWithUpperBound() {
    return false;
  }

  public DynamicTypeWithUpperBound asDynamicTypeWithUpperBound() {
    return null;
  }

  public boolean isExactClassType() {
    return getExactClassType() != null;
  }

  public boolean isNullType() {
    return false;
  }

  public boolean isNotNullType() {
    return false;
  }

  public boolean isUnknown() {
    return false;
  }

  public DynamicType join(AppView<AppInfoWithLiveness> appView, DynamicType dynamicType) {
    if (isBottom()) {
      return dynamicType;
    }
    if (dynamicType.isBottom() || equals(dynamicType)) {
      return this;
    }
    if (isUnknown() || dynamicType.isUnknown()) {
      return unknown();
    }
    if (isNotNullType() || dynamicType.isNotNullType()) {
      if (getNullability().isNullable() || dynamicType.getNullability().isNullable()) {
        return unknown();
      }
      return definitelyNotNull();
    }
    assert isDynamicTypeWithUpperBound();
    assert dynamicType.isDynamicTypeWithUpperBound();
    return asDynamicTypeWithUpperBound().join(appView, dynamicType.asDynamicTypeWithUpperBound());
  }

  public abstract DynamicType rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, Set<DexType> prunedTypes);

  public abstract DynamicType withNullability(Nullability nullability);

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  private static boolean verifyNotEffectivelyFinalClassType(
      AppView<AppInfoWithLiveness> appView, TypeElement type) {
    if (type.isClassType()) {
      ClassTypeElement classType = type.asClassType();
      DexClass clazz = appView.definitionFor(classType.getClassType());
      assert clazz == null || !clazz.isEffectivelyFinal(appView);
    }
    return true;
  }
}
