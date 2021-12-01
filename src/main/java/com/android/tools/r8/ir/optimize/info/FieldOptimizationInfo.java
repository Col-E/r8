// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public abstract class FieldOptimizationInfo
    implements MemberOptimizationInfo<MutableFieldOptimizationInfo> {

  public abstract boolean cannotBeKept();

  public abstract AbstractValue getAbstractValue();

  /**
   * This should only be used once all methods in the program have been processed. Until then the
   * value returned by this method may not be sound.
   */
  public abstract int getReadBits();

  public abstract DynamicType getDynamicType();

  public abstract boolean isDead();

  public abstract boolean valueHasBeenPropagated();

  @Override
  public boolean isFieldOptimizationInfo() {
    return true;
  }

  @Override
  public FieldOptimizationInfo asFieldOptimizationInfo() {
    return this;
  }
}
