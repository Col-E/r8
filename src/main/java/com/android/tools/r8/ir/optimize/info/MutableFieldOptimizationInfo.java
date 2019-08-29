// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

/**
 * Optimization info for fields.
 *
 * <p>NOTE: Unlike the optimization info for methods, the field optimization info is currently being
 * updated directly, meaning that updates may become visible to concurrently processed methods in
 * the {@link com.android.tools.r8.ir.conversion.IRConverter}.
 */
public class MutableFieldOptimizationInfo extends FieldOptimizationInfo {

  private boolean cannotBeKept = false;
  private boolean valueHasBeenPropagated = false;

  public MutableFieldOptimizationInfo mutableCopy() {
    MutableFieldOptimizationInfo copy = new MutableFieldOptimizationInfo();
    copy.cannotBeKept = cannotBeKept();
    copy.valueHasBeenPropagated = valueHasBeenPropagated();
    return copy;
  }

  @Override
  public boolean cannotBeKept() {
    return cannotBeKept;
  }

  public void markCannotBeKept() {
    cannotBeKept = true;
  }

  @Override
  public boolean valueHasBeenPropagated() {
    return valueHasBeenPropagated;
  }

  public void markAsPropagated() {
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
