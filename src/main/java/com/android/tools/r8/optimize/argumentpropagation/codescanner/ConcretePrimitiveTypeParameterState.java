// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ConcretePrimitiveTypeParameterState extends ConcreteParameterState {

  private AbstractValue abstractValue;

  public ConcretePrimitiveTypeParameterState(AbstractValue abstractValue) {
    assert !abstractValue.isUnknown() : "Must use UnknownParameterState";
    this.abstractValue = abstractValue;
  }

  public ConcretePrimitiveTypeParameterState(MethodParameter inParameter) {
    super(inParameter);
    this.abstractValue = AbstractValue.bottom();
  }

  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ConcretePrimitiveTypeParameterState parameterState) {
    boolean allowNullOrAbstractValue = false;
    boolean allowNonConstantNumbers = false;
    abstractValue =
        abstractValue.join(
            parameterState.abstractValue,
            appView.abstractValueFactory(),
            allowNullOrAbstractValue,
            allowNonConstantNumbers);
    if (abstractValue.isUnknown()) {
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
    return ConcreteParameterStateKind.PRIMITIVE;
  }

  @Override
  public boolean isPrimitiveParameter() {
    return true;
  }

  @Override
  public ConcretePrimitiveTypeParameterState asPrimitiveParameter() {
    return this;
  }
}
