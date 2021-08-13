// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class ConcreteClassTypeParameterState extends ConcreteParameterState {

  private AbstractValue abstractValue;
  private DynamicType dynamicType;

  public ConcreteClassTypeParameterState(MethodParameter inParameter) {
    super(inParameter);
    this.abstractValue = AbstractValue.bottom();
    this.dynamicType = DynamicType.bottom();
  }

  public ConcreteClassTypeParameterState(AbstractValue abstractValue, DynamicType dynamicType) {
    this.abstractValue = abstractValue;
    this.dynamicType = dynamicType;
  }

  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteClassTypeParameterState parameterState,
      Action onChangedAction) {
    boolean allowNullOrAbstractValue = true;
    boolean allowNonConstantNumbers = false;
    AbstractValue oldAbstractValue = abstractValue;
    abstractValue =
        abstractValue.join(
            parameterState.abstractValue,
            appView.abstractValueFactory(),
            allowNullOrAbstractValue,
            allowNonConstantNumbers);
    // TODO(b/190154391): Join the dynamic types using SubtypingInfo.
    // TODO(b/190154391): Take in the static type as an argument, and unset the dynamic type if it
    //  equals the static type.
    DynamicType oldDynamicType = dynamicType;
    dynamicType =
        dynamicType.equals(parameterState.dynamicType) ? dynamicType : DynamicType.unknown();
    if (abstractValue.isUnknown() && dynamicType.isUnknown()) {
      return unknown();
    }
    boolean inParametersChanged = mutableJoinInParameters(parameterState);
    if (widenInParameters()) {
      return unknown();
    }
    if (abstractValue != oldAbstractValue || dynamicType != oldDynamicType || inParametersChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.CLASS;
  }

  @Override
  public boolean isClassParameter() {
    return true;
  }

  @Override
  public ConcreteClassTypeParameterState asClassParameter() {
    return this;
  }
}
