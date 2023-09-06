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
import java.util.Objects;
import java.util.Set;

/**
 * Represents the runtime type of a reference value. This type may be more precise than the value's
 * statically declared type.
 *
 * <p>If a lower bound is known on the runtime type (e.g., {@code new A()}), then {@link
 * DynamicTypeWithLowerBound} is used.
 */
public class DynamicTypeWithUpperBound extends DynamicType {

  static final DynamicTypeWithUpperBound BOTTOM =
      new DynamicTypeWithUpperBound(TypeElement.getBottom());
  static final DynamicTypeWithUpperBound NULL_TYPE =
      new DynamicTypeWithUpperBound(TypeElement.getNull());
  static final DynamicTypeWithUpperBound UNKNOWN =
      new DynamicTypeWithUpperBound(TypeElement.getTop());

  private final TypeElement dynamicUpperBoundType;

  DynamicTypeWithUpperBound(TypeElement dynamicUpperBoundType) {
    assert dynamicUpperBoundType != null;
    this.dynamicUpperBoundType = dynamicUpperBoundType;
  }

  public static DynamicTypeWithUpperBound create(
      AppView<AppInfoWithLiveness> appView, TypeElement dynamicUpperBoundType) {
    ClassTypeElement dynamicLowerBoundType =
        isEffectivelyFinal(appView, dynamicUpperBoundType)
            ? dynamicUpperBoundType.asClassType()
            : null;
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

  public static DynamicTypeWithUpperBound create(
      AppView<AppInfoWithLiveness> appView, Value value) {
    assert value.getType().isReferenceType();
    TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
    ClassTypeElement dynamicLowerBoundType =
        value.getDynamicLowerBoundType(
            appView, dynamicUpperBoundType, dynamicUpperBoundType.nullability());
    return create(appView, dynamicUpperBoundType, dynamicLowerBoundType);
  }

  private static boolean isEffectivelyFinal(AppView<?> appView, TypeElement type) {
    if (type.isClassType()) {
      ClassTypeElement classType = type.asClassType();
      DexClass clazz = appView.definitionFor(classType.getClassType());
      return clazz != null && clazz.isEffectivelyFinal(appView);
    }
    return false;
  }

  @Override
  public boolean hasDynamicUpperBoundType() {
    return true;
  }

  @Override
  public TypeElement getDynamicUpperBoundType(TypeElement staticType) {
    return getDynamicUpperBoundType();
  }

  public TypeElement getDynamicUpperBoundType() {
    return dynamicUpperBoundType;
  }

  @Override
  public boolean hasDynamicLowerBoundType() {
    return false;
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return null;
  }

  @Override
  public boolean isExactClassType() {
    return getExactClassType() != null;
  }

  @Override
  public ClassTypeElement getExactClassType() {
    return hasDynamicLowerBoundType()
            && getDynamicLowerBoundType().equalUpToNullability(getDynamicUpperBoundType())
        ? getDynamicLowerBoundType()
        : null;
  }

  @Override
  public Nullability getNullability() {
    return dynamicUpperBoundType.nullability();
  }

  @Override
  public boolean isBottom() {
    return dynamicUpperBoundType.isBottom();
  }

  @Override
  public boolean isDynamicTypeWithUpperBound() {
    return true;
  }

  @Override
  public DynamicTypeWithUpperBound asDynamicTypeWithUpperBound() {
    return this;
  }

  @Override
  public boolean isNullType() {
    return dynamicUpperBoundType.isNullType();
  }

  @Override
  public boolean isUnknown() {
    return dynamicUpperBoundType.isTop();
  }

  public DynamicType join(
      AppView<AppInfoWithLiveness> appView, DynamicTypeWithUpperBound dynamicType) {
    TypeElement upperBoundType =
        getDynamicUpperBoundType().join(dynamicType.getDynamicUpperBoundType(), appView);
    ClassTypeElement lowerBoundType =
        isEffectivelyFinal(appView, upperBoundType)
            ? upperBoundType.asClassType()
            : meetDynamicLowerBound(appView, dynamicType);
    if (upperBoundType.equals(getDynamicUpperBoundType())
        && Objects.equals(lowerBoundType, getDynamicLowerBoundType())) {
      return this;
    }
    return create(appView, upperBoundType, lowerBoundType);
  }

  private ClassTypeElement meetDynamicLowerBound(
      AppView<AppInfoWithLiveness> appView, DynamicType dynamicType) {
    if (isNullType()) {
      if (dynamicType.hasDynamicLowerBoundType()) {
        return dynamicType.getDynamicLowerBoundType().joinNullability(Nullability.definitelyNull());
      }
      return null;
    }
    if (dynamicType.isNullType()) {
      if (hasDynamicLowerBoundType()) {
        return getDynamicLowerBoundType().joinNullability(Nullability.definitelyNull());
      }
      return null;
    }
    if (!hasDynamicLowerBoundType() || !dynamicType.hasDynamicLowerBoundType()) {
      return null;
    }
    ClassTypeElement lowerBoundType = getDynamicLowerBoundType();
    ClassTypeElement otherLowerBoundType = dynamicType.getDynamicLowerBoundType();
    if (lowerBoundType.lessThanOrEqualUpToNullability(otherLowerBoundType, appView)) {
      return lowerBoundType.joinNullability(otherLowerBoundType.nullability());
    }
    if (otherLowerBoundType.lessThanOrEqualUpToNullability(lowerBoundType, appView)) {
      return otherLowerBoundType.joinNullability(lowerBoundType.nullability());
    }
    return null;
  }

  @Override
  public DynamicType rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, Set<DexType> prunedTypes) {
    if (isBottom() || isNullType() || isUnknown()) {
      return this;
    }
    TypeElement rewrittenDynamicUpperBoundType =
        dynamicUpperBoundType.rewrittenWithLens(appView, graphLens, null, prunedTypes);
    ClassTypeElement rewrittenDynamicLowerBoundClassType = null;
    if (hasDynamicLowerBoundType()) {
      TypeElement rewrittenDynamicLowerBoundType =
          getDynamicLowerBoundType().rewrittenWithLens(appView, graphLens, null, prunedTypes);
      if (rewrittenDynamicLowerBoundType.isClassType()) {
        rewrittenDynamicLowerBoundClassType = rewrittenDynamicLowerBoundType.asClassType();
      }
    }
    return rewrittenDynamicLowerBoundClassType != null
        ? create(appView, rewrittenDynamicUpperBoundType, rewrittenDynamicLowerBoundClassType)
        : create(appView, rewrittenDynamicUpperBoundType);
  }

  public boolean strictlyLessThan(TypeElement type, AppView<AppInfoWithLiveness> appView) {
    DynamicTypeWithUpperBound dynamicType = create(appView, type);
    return strictlyLessThan(dynamicType, appView);
  }

  public boolean strictlyLessThan(DynamicTypeWithUpperBound dynamicType, AppView<?> appView) {
    if (equals(dynamicType)) {
      return false;
    }
    if (getDynamicUpperBoundType().equals(dynamicType.getDynamicUpperBoundType())) {
      if (!dynamicType.hasDynamicLowerBoundType()) {
        return hasDynamicLowerBoundType();
      }
      return hasDynamicLowerBoundType()
          && dynamicType
              .getDynamicLowerBoundType()
              .strictlyLessThan(getDynamicLowerBoundType(), appView);
    }
    if (!getDynamicUpperBoundType()
        .strictlyLessThan(dynamicType.getDynamicUpperBoundType(), appView)) {
      return false;
    }
    if (!dynamicType.hasDynamicLowerBoundType()) {
      return true;
    }
    return hasDynamicLowerBoundType()
        && dynamicType
            .getDynamicLowerBoundType()
            .lessThanOrEqualUpToNullability(getDynamicUpperBoundType(), appView);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DynamicTypeWithUpperBound dynamicType = (DynamicTypeWithUpperBound) other;
    return dynamicUpperBoundType.equals(dynamicType.dynamicUpperBoundType);
  }

  @Override
  public int hashCode() {
    return dynamicUpperBoundType.hashCode();
  }

  @Override
  public String toString() {
    return "DynamicTypeWithUpperBound(upperBound=" + getDynamicUpperBoundType() + ")";
  }

  private static boolean verifyNotEffectivelyFinalClassType(
      AppView<AppInfoWithLiveness> appView, TypeElement type) {
    if (type.isClassType()) {
      ClassTypeElement classType = type.asClassType();
      DexClass clazz = appView.definitionFor(classType.getClassType());
      assert clazz == null || !clazz.isEffectivelyFinal(appView);
    }
    return true;
  }

  @Override
  public DynamicTypeWithUpperBound withNullability(Nullability nullability) {
    assert !hasDynamicLowerBoundType();
    if (!getDynamicUpperBoundType().isReferenceType()) {
      return this;
    }
    ReferenceTypeElement dynamicUpperBoundReferenceType =
        getDynamicUpperBoundType().asReferenceType();
    if (dynamicUpperBoundReferenceType.nullability() == nullability) {
      return this;
    }
    return new DynamicTypeWithUpperBound(
        dynamicUpperBoundReferenceType.getOrCreateVariant(nullability));
  }
}
