// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;

/** The information that we track for fields whose type is a primitive type. */
public class ConcretePrimitiveTypeFieldState extends ConcreteFieldState {

  private AbstractValue abstractValue;

  ConcretePrimitiveTypeFieldState(AbstractValue abstractValue) {
    this.abstractValue = abstractValue;
  }

  public static FieldState create(AbstractValue abstractValue) {
    return abstractValue.isUnknown()
        ? FieldState.unknown()
        : new ConcretePrimitiveTypeFieldState(abstractValue);
  }

  @Override
  public AbstractValue getAbstractValue(AbstractValueFactory abstractValueFactory) {
    return abstractValue;
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public ConcretePrimitiveTypeFieldState asPrimitive() {
    return this;
  }

  public FieldState mutableJoin(
      AbstractValue abstractValue, AbstractValueFactory abstractValueFactory) {
    if (abstractValue.isUnknown()) {
      return FieldState.unknown();
    }
    this.abstractValue = this.abstractValue.joinPrimitive(abstractValue, abstractValueFactory);
    return isEffectivelyUnknown() ? unknown() : this;
  }

  private boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
  }
}
