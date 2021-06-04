// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class FieldOptimizationInfo
    implements MemberOptimizationInfo<MutableFieldOptimizationInfo> {

  public abstract boolean cannotBeKept();

  public abstract AbstractValue getAbstractValue();

  /**
   * This should only be used once all methods in the program have been processed. Until then the
   * value returned by this method may not be sound.
   */
  public abstract int getReadBits();

  public abstract ClassTypeElement getDynamicLowerBoundType();

  public abstract TypeElement getDynamicUpperBoundType();

  public ClassTypeElement getExactClassType(AppView<AppInfoWithLiveness> appView) {
    ClassTypeElement dynamicLowerBoundType = getDynamicLowerBoundType();
    TypeElement dynamicUpperBoundType = getDynamicUpperBoundType();
    if (dynamicUpperBoundType == null || !dynamicUpperBoundType.isClassType()) {
      return null;
    }
    DexType upperType = dynamicUpperBoundType.asClassType().getClassType();
    if (dynamicLowerBoundType != null && upperType == dynamicLowerBoundType.getClassType()) {
      return dynamicLowerBoundType;
    }
    DexClass upperClass = appView.definitionFor(upperType);
    if (upperClass != null && upperClass.isEffectivelyFinal(appView)) {
      assert dynamicLowerBoundType == null;
      return ClassTypeElement.create(upperType, dynamicUpperBoundType.nullability(), appView);
    }
    return null;
  }

  public final TypeElement getDynamicUpperBoundTypeOrElse(TypeElement orElse) {
    TypeElement dynamicUpperBoundType = getDynamicUpperBoundType();
    return dynamicUpperBoundType != null ? dynamicUpperBoundType : orElse;
  }

  public abstract boolean isDead();

  public abstract boolean valueHasBeenPropagated();
}
