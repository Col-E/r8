// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import java.util.Objects;

/**
 * Used to represent that a constructor initializes an instance field on the newly created instance
 * with a known dynamic lower- and upper-bound type.
 */
public class InstanceFieldTypeInitializationInfo implements InstanceFieldInitializationInfo {

  private final ClassTypeLatticeElement dynamicLowerBoundType;
  private final TypeLatticeElement dynamicUpperBoundType;

  /** Intentionally package private, use {@link InstanceFieldInitializationInfoFactory} instead. */
  InstanceFieldTypeInitializationInfo(
      ClassTypeLatticeElement dynamicLowerBoundType, TypeLatticeElement dynamicUpperBoundType) {
    this.dynamicLowerBoundType = dynamicLowerBoundType;
    this.dynamicUpperBoundType = dynamicUpperBoundType;
  }

  public ClassTypeLatticeElement getDynamicLowerBoundType() {
    return dynamicLowerBoundType;
  }

  public TypeLatticeElement getDynamicUpperBoundType() {
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
  public int hashCode() {
    return Objects.hash(dynamicLowerBoundType, dynamicUpperBoundType);
  }

  @Override
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
}
