// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomArrayTypeParameterState extends BottomParameterState {

  private static final BottomArrayTypeParameterState INSTANCE = new BottomArrayTypeParameterState();

  private BottomArrayTypeParameterState() {}

  public static BottomArrayTypeParameterState get() {
    return INSTANCE;
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ParameterState parameterState,
      DexType parameterType,
      StateCloner cloner,
      Action onChangedAction) {
    if (parameterState.isBottom()) {
      return this;
    }
    if (parameterState.isUnknown()) {
      return parameterState;
    }
    assert parameterState.isConcrete();
    assert parameterState.asConcrete().isReferenceParameter();
    ConcreteReferenceTypeParameterState concreteParameterState =
        parameterState.asConcrete().asReferenceParameter();
    if (concreteParameterState.isArrayParameter()) {
      return cloner.mutableCopy(concreteParameterState);
    }
    Nullability nullability = concreteParameterState.getNullability();
    if (nullability.isMaybeNull()) {
      return unknown();
    }
    return new ConcreteArrayTypeParameterState(
        nullability, concreteParameterState.copyInParameters());
  }
}
