// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomClassTypeParameterState extends BottomParameterState {

  private static final BottomClassTypeParameterState INSTANCE = new BottomClassTypeParameterState();

  private BottomClassTypeParameterState() {}

  public static BottomClassTypeParameterState get() {
    return INSTANCE;
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ParameterState parameterState,
      DexType parameterType,
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
    AbstractValue abstractValue = concreteParameterState.getAbstractValue(appView);
    DynamicType dynamicType = concreteParameterState.getDynamicType();
    DynamicType widenedDynamicType =
        WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, parameterType);
    return abstractValue.isUnknown() && widenedDynamicType.isUnknown()
        ? unknown()
        : new ConcreteClassTypeParameterState(
            abstractValue, widenedDynamicType, concreteParameterState.copyInParameters());
  }
}
