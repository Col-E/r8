// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

public class ConcretePrimitiveTypeParameterState extends ConcreteParameterState {

  private AbstractValue abstractValue;

  public ConcretePrimitiveTypeParameterState(AbstractValue abstractValue) {
    this(abstractValue, Collections.emptySet());
  }

  public ConcretePrimitiveTypeParameterState(
      AbstractValue abstractValue, Set<MethodParameter> inParameters) {
    super(inParameters);
    this.abstractValue = abstractValue;
    assert !isEffectivelyBottom() : "Must use BottomPrimitiveTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  public ConcretePrimitiveTypeParameterState(MethodParameter inParameter) {
    this(AbstractValue.bottom(), SetUtils.newHashSet(inParameter));
  }

  @Override
  public ParameterState clearInParameters() {
    if (hasInParameters()) {
      if (abstractValue.isBottom()) {
        return bottomPrimitiveTypeParameter();
      }
      internalClearInParameters();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public ParameterState mutableCopy() {
    return new ConcretePrimitiveTypeParameterState(abstractValue, copyInParameters());
  }

  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcretePrimitiveTypeParameterState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    assert parameterType.isPrimitiveType();
    AbstractValue oldAbstractValue = abstractValue;
    abstractValue =
        appView
            .getAbstractValueParameterJoiner()
            .join(abstractValue, parameterState.abstractValue, parameterType);
    if (abstractValue.isUnknown()) {
      return unknown();
    }
    boolean inParametersChanged = mutableJoinInParameters(parameterState);
    if (widenInParameters(appView)) {
      return unknown();
    }
    if (abstractValue != oldAbstractValue || inParametersChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return abstractValue;
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.PRIMITIVE;
  }

  public boolean isEffectivelyBottom() {
    return abstractValue.isBottom() && !hasInParameters();
  }

  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
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
