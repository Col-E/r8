// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.ir.analysis.type.Nullability;

public class ConcreteArrayTypeParameterState extends ConcreteParameterState {

  private Nullability nullability;

  public ConcreteArrayTypeParameterState(MethodParameter inParameter) {
    super(inParameter);
    this.nullability = Nullability.bottom();
  }

  public ConcreteArrayTypeParameterState(Nullability nullability) {
    assert !nullability.isMaybeNull() : "Must use UnknownParameterState instead";
    this.nullability = nullability;
  }

  public ParameterState mutableJoin(ConcreteArrayTypeParameterState parameterState) {
    assert !nullability.isMaybeNull();
    assert !parameterState.nullability.isMaybeNull();
    mutableJoinInParameters(parameterState);
    if (widenInParameters()) {
      return unknown();
    }
    return this;
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.ARRAY;
  }

  @Override
  public boolean isArrayParameter() {
    return true;
  }

  @Override
  public ConcreteArrayTypeParameterState asArrayParameter() {
    return this;
  }
}
