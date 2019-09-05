// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;

/**
 * Optimization info for fields.
 *
 * <p>NOTE: Unlike the optimization info for methods, the field optimization info is currently being
 * updated directly, meaning that updates may become visible to concurrently processed methods in
 * the {@link com.android.tools.r8.ir.conversion.IRConverter}.
 */
public class MutableFieldOptimizationInfo extends FieldOptimizationInfo {

  private int readBits = 0;
  private boolean cannotBeKept = false;
  private boolean valueHasBeenPropagated = false;
  private TypeLatticeElement dynamicType = null;

  @Override
  public MutableFieldOptimizationInfo mutableCopy() {
    MutableFieldOptimizationInfo copy = new MutableFieldOptimizationInfo();
    copy.cannotBeKept = cannotBeKept();
    copy.valueHasBeenPropagated = valueHasBeenPropagated();
    return copy;
  }

  @Override
  public int getReadBits() {
    return readBits;
  }

  void setReadBits(int readBits) {
    this.readBits = readBits;
  }

  @Override
  public boolean cannotBeKept() {
    return cannotBeKept;
  }

  void markCannotBeKept() {
    cannotBeKept = true;
  }

  @Override
  public TypeLatticeElement getDynamicType() {
    return dynamicType;
  }

  void setDynamicType(TypeLatticeElement type) {
    dynamicType = type;
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
