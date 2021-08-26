// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

/**
 * Represents the runtime type of a reference value. This type may be more precise than the value's
 * statically declared type.
 *
 * <p>If a lower bound is known on the runtime type (e.g., {@code new A()}), then {@link
 * DynamicTypeWithLowerBound} is used.
 */
public class DynamicType {

  private static final DynamicType BOTTOM = new DynamicType(TypeElement.getBottom());
  private static final DynamicType NULL_TYPE = new DynamicType(TypeElement.getNull());
  private static final DynamicType UNKNOWN = new DynamicType(TypeElement.getTop());

  private final TypeElement dynamicUpperBoundType;

  DynamicType(TypeElement dynamicUpperBoundType) {
    assert dynamicUpperBoundType != null;
    this.dynamicUpperBoundType = dynamicUpperBoundType;
  }

  public static DynamicType create(
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

  public static DynamicType create(
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
    return new DynamicType(dynamicUpperBoundType);
  }

  public static DynamicType createExact(ClassTypeElement exactDynamicType) {
    return new ExactDynamicType(exactDynamicType);
  }

  public static DynamicType create(AppView<AppInfoWithLiveness> appView, Value value) {
    assert value.getType().isReferenceType();
    TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
    ClassTypeElement dynamicLowerBoundType =
        value.getDynamicLowerBoundType(
            appView, dynamicUpperBoundType, dynamicUpperBoundType.nullability());
    return create(appView, dynamicUpperBoundType, dynamicLowerBoundType);
  }

  public static DynamicType bottom() {
    return BOTTOM;
  }

  public static DynamicType definitelyNull() {
    return NULL_TYPE;
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

  public Nullability getNullability() {
    return getDynamicUpperBoundType().nullability();
  }

  public boolean isBottom() {
    return getDynamicUpperBoundType().isBottom();
  }

  public boolean isNullType() {
    return getDynamicUpperBoundType().isNullType();
  }

  public boolean isUnknown() {
    return getDynamicUpperBoundType().isTop();
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
    TypeElement upperBoundType =
        getDynamicUpperBoundType().join(dynamicType.getDynamicUpperBoundType(), appView);
    ClassTypeElement lowerBoundType = meetDynamicLowerBound(appView, dynamicType);
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
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DynamicType dynamicType = (DynamicType) other;
    return dynamicUpperBoundType.equals(dynamicType.dynamicUpperBoundType);
  }

  @Override
  public int hashCode() {
    return dynamicUpperBoundType.hashCode();
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

  public DynamicType withNullability(Nullability nullability) {
    assert !hasDynamicLowerBoundType();
    if (!getDynamicUpperBoundType().isReferenceType()) {
      return this;
    }
    ReferenceTypeElement dynamicUpperBoundReferenceType =
        getDynamicUpperBoundType().asReferenceType();
    if (dynamicUpperBoundReferenceType.nullability() == nullability) {
      return this;
    }
    return new DynamicType(dynamicUpperBoundReferenceType.getOrCreateVariant(nullability));
  }
}
