// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;

/**
 * Optimization info for fields.
 *
 * <p>NOTE: Unlike the optimization info for methods, the field optimization info is currently being
 * updated directly, meaning that updates may become visible to concurrently processed methods in
 * the {@link com.android.tools.r8.ir.conversion.IRConverter}.
 */
public class MutableFieldOptimizationInfo extends FieldOptimizationInfo {

  private AbstractValue abstractValue = UnknownValue.getInstance();
  private int readBits = 0;
  private boolean cannotBeKept = false;
  private boolean valueHasBeenPropagated = false;
  private ClassTypeLatticeElement dynamicLowerBoundType = null;
  private TypeLatticeElement dynamicUpperBoundType = null;

  public void fixupClassTypeReferences(
      Function<DexType, DexType> mapping, AppView<? extends AppInfoWithSubtyping> appView) {
    if (dynamicUpperBoundType != null) {
      dynamicUpperBoundType = dynamicUpperBoundType.fixupClassTypeReferences(mapping, appView);
    }
    if (dynamicLowerBoundType != null) {
      TypeLatticeElement dynamicLowerBoundType =
          this.dynamicLowerBoundType.fixupClassTypeReferences(mapping, appView);
      if (dynamicLowerBoundType.isClassType()) {
        this.dynamicLowerBoundType = dynamicLowerBoundType.asClassTypeLatticeElement();
      } else {
        assert dynamicLowerBoundType.isPrimitive();
        this.dynamicLowerBoundType = null;
        this.dynamicUpperBoundType = null;
      }
    }
  }

  @Override
  public MutableFieldOptimizationInfo mutableCopy() {
    MutableFieldOptimizationInfo copy = new MutableFieldOptimizationInfo();
    copy.cannotBeKept = cannotBeKept();
    copy.valueHasBeenPropagated = valueHasBeenPropagated();
    return copy;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  public void setAbstractValue(AbstractValue abstractValue) {
    this.abstractValue = abstractValue;
  }

  public void fixupAbstractValue(AppView<AppInfoWithLiveness> appView, GraphLense lens) {
    abstractValue = abstractValue.rewrittenWithLens(appView, lens);
  }

  @Override
  public int getReadBits() {
    return readBits;
  }

  void joinReadBits(int readBits) {
    this.readBits |= readBits;
  }

  @Override
  public boolean cannotBeKept() {
    return cannotBeKept;
  }

  void markCannotBeKept() {
    cannotBeKept = true;
  }

  @Override
  public ClassTypeLatticeElement getDynamicLowerBoundType() {
    return dynamicLowerBoundType;
  }

  void setDynamicLowerBoundType(ClassTypeLatticeElement type) {
    dynamicLowerBoundType = type;
  }

  @Override
  public TypeLatticeElement getDynamicUpperBoundType() {
    return dynamicUpperBoundType;
  }

  void setDynamicUpperBoundType(TypeLatticeElement type) {
    dynamicUpperBoundType = type;
  }

  @Override
  public boolean valueHasBeenPropagated() {
    return valueHasBeenPropagated;
  }

  void markAsPropagated() {
    valueHasBeenPropagated = true;
  }

  @Override
  public boolean isMutableFieldOptimizationInfo() {
    return true;
  }

  @Override
  public MutableFieldOptimizationInfo asMutableFieldOptimizationInfo() {
    return this;
  }
}
