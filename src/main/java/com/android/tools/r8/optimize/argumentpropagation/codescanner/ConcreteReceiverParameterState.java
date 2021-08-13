// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.utils.Action;

public class ConcreteReceiverParameterState extends ConcreteParameterState {

  private DynamicType dynamicType;

  public ConcreteReceiverParameterState(DynamicType dynamicType) {
    this.dynamicType = dynamicType;
  }

  public ParameterState mutableJoin(
      ConcreteReceiverParameterState parameterState, Action onChangedAction) {
    // TODO(b/190154391): Join the dynamic types using SubtypingInfo.
    // TODO(b/190154391): Take in the static type as an argument, and unset the dynamic type if it
    //  equals the static type.
    DynamicType oldDynamicType = dynamicType;
    dynamicType =
        dynamicType.equals(parameterState.dynamicType) ? dynamicType : DynamicType.unknown();
    if (dynamicType.isUnknown()) {
      return unknown();
    }
    boolean inParametersChanged = mutableJoinInParameters(parameterState);
    if (widenInParameters()) {
      return unknown();
    }
    if (dynamicType != oldDynamicType || inParametersChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return AbstractValue.unknown();
  }

  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.RECEIVER;
  }

  @Override
  public boolean isReceiverParameter() {
    return true;
  }

  @Override
  public ConcreteReceiverParameterState asReceiverParameter() {
    return this;
  }
}
