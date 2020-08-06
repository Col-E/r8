// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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

  private static final int FLAGS_CANNOT_BE_KEPT = 1 << 0;
  private static final int FLAGS_IS_DEAD = 1 << 1;
  private static final int FLAGS_VALUE_HAS_BEEN_PROPAGATED = 1 << 2;

  private AbstractValue abstractValue = UnknownValue.getInstance();
  private int flags;
  private int readBits = 0;
  private ClassTypeElement dynamicLowerBoundType = null;
  private TypeElement dynamicUpperBoundType = null;

  public MutableFieldOptimizationInfo fixupClassTypeReferences(
      Function<DexType, DexType> mapping, AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (dynamicUpperBoundType != null) {
      dynamicUpperBoundType = dynamicUpperBoundType.fixupClassTypeReferences(mapping, appView);
    }
    if (dynamicLowerBoundType != null) {
      TypeElement dynamicLowerBoundType =
          this.dynamicLowerBoundType.fixupClassTypeReferences(mapping, appView);
      if (dynamicLowerBoundType.isClassType()) {
        this.dynamicLowerBoundType = dynamicLowerBoundType.asClassType();
      } else {
        assert dynamicLowerBoundType.isPrimitiveType();
        this.dynamicLowerBoundType = null;
        this.dynamicUpperBoundType = null;
      }
    }
    return this;
  }

  @Override
  public MutableFieldOptimizationInfo mutableCopy() {
    MutableFieldOptimizationInfo copy = new MutableFieldOptimizationInfo();
    copy.flags = flags;
    return copy;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  void setAbstractValue(AbstractValue abstractValue) {
    this.abstractValue = abstractValue;
  }

  public void fixupAbstractValue(AppView<AppInfoWithLiveness> appView, GraphLens lens) {
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
    return (flags & FLAGS_CANNOT_BE_KEPT) != 0;
  }

  void markCannotBeKept() {
    flags |= FLAGS_CANNOT_BE_KEPT;
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return dynamicLowerBoundType;
  }

  void setDynamicLowerBoundType(ClassTypeElement type) {
    dynamicLowerBoundType = type;
  }

  @Override
  public TypeElement getDynamicUpperBoundType() {
    return dynamicUpperBoundType;
  }

  void setDynamicUpperBoundType(TypeElement type) {
    dynamicUpperBoundType = type;
  }

  @Override
  public boolean isDead() {
    return (flags & FLAGS_IS_DEAD) != 0;
  }

  void markAsDead() {
    flags |= FLAGS_IS_DEAD;
  }

  @Override
  public boolean valueHasBeenPropagated() {
    return (flags & FLAGS_VALUE_HAS_BEEN_PROPAGATED) != 0;
  }

  void markAsPropagated() {
    flags |= FLAGS_VALUE_HAS_BEEN_PROPAGATED;
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
