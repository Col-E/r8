// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

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
      AppView<AppInfoWithLiveness> appView, ConcreteClassTypeParameterState parameterState) {
    boolean allowNullOrAbstractValue = true;
    boolean allowNonConstantNumbers = false;
    abstractValue =
        abstractValue.join(
            parameterState.abstractValue,
            appView.abstractValueFactory(),
            allowNullOrAbstractValue,
            allowNonConstantNumbers);
    // TODO(b/190154391): Join the dynamic types using SubtypingInfo.
    // TODO(b/190154391): Take in the static type as an argument, and unset the dynamic type if it
    //  equals the static type.
    dynamicType =
        dynamicType.equals(parameterState.dynamicType) ? dynamicType : DynamicType.unknown();
    if (abstractValue.isUnknown() && dynamicType.isUnknown()) {
      return unknown();
    }
    mutableJoinInParameters(parameterState);
    if (widenInParameters()) {
      return unknown();
    }
    return this;
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
