// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

/**
 * Used to represent that a constructor initializes an instance field on the newly created instance
 * with a known dynamic lower- and upper-bound type.
 */
public class InstanceFieldTypeInitializationInfo implements InstanceFieldInitializationInfo {

  private final ClassTypeElement dynamicLowerBoundType;
  private final TypeElement dynamicUpperBoundType;

  /** Intentionally package private, use {@link InstanceFieldInitializationInfoFactory} instead. */
  InstanceFieldTypeInitializationInfo(
      ClassTypeElement dynamicLowerBoundType, TypeElement dynamicUpperBoundType) {
    this.dynamicLowerBoundType = dynamicLowerBoundType;
    this.dynamicUpperBoundType = dynamicUpperBoundType;
  }

  public ClassTypeElement getDynamicLowerBoundType() {
    return dynamicLowerBoundType;
  }

  public TypeElement getDynamicUpperBoundType() {
    return dynamicUpperBoundType;
  }

  @Override
  public boolean isTypeInitializationInfo() {
    return true;
  }

  @Override
  public InstanceFieldTypeInitializationInfo asTypeInitializationInfo() {
    return this;
  }

  @Override
  public InstanceFieldInitializationInfo fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  public InstanceFieldInitializationInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    EnumDataMap enumDataMap = appView.unboxedEnums();
    if (dynamicLowerBoundType != null
        && enumDataMap.isUnboxedEnum(dynamicLowerBoundType.getClassType())) {
      // No point in tracking the type of primitives.
      return UnknownInstanceFieldInitializationInfo.getInstance();
    }
    if (dynamicUpperBoundType.isClassType()
        && enumDataMap.isUnboxedEnum(dynamicUpperBoundType.asClassType().getClassType())) {
      // No point in tracking the type of primitives.
      return UnknownInstanceFieldInitializationInfo.getInstance();
    }
    return new InstanceFieldTypeInitializationInfo(
        dynamicLowerBoundType != null
            ? dynamicLowerBoundType.rewrittenWithLens(appView, lens, codeLens).asClassType()
            : null,
        dynamicUpperBoundType.rewrittenWithLens(appView, lens, codeLens));
  }

  @Override
  public int hashCode() {
    return Objects.hash(dynamicLowerBoundType, dynamicUpperBoundType);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (getClass() != other.getClass()) {
      return false;
    }
    InstanceFieldTypeInitializationInfo info = (InstanceFieldTypeInitializationInfo) other;
    return Objects.equals(dynamicLowerBoundType, info.dynamicLowerBoundType)
        && Objects.equals(dynamicUpperBoundType, info.dynamicUpperBoundType);
  }

  @Override
  public String toString() {
    return "InstanceFieldTypeInitializationInfo";
  }
}
