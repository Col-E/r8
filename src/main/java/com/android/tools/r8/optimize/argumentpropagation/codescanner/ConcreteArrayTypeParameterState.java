// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

public class ConcreteArrayTypeParameterState extends ConcreteReferenceTypeParameterState {

  private Nullability nullability;

  public ConcreteArrayTypeParameterState(MethodParameter inParameter) {
    this(Nullability.bottom(), SetUtils.newHashSet(inParameter));
  }

  public ConcreteArrayTypeParameterState(Nullability nullability) {
    this(nullability, Collections.emptySet());
  }

  public ConcreteArrayTypeParameterState(
      Nullability nullability, Set<MethodParameter> inParameters) {
    super(inParameters);
    this.nullability = nullability;
    assert !isEffectivelyBottom() : "Must use BottomArrayTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  @Override
  public ParameterState clearInParameters() {
    if (hasInParameters()) {
      if (nullability.isBottom()) {
        return bottomArrayTypeParameter();
      }
      internalClearInParameters();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    if (getNullability().isDefinitelyNull()) {
      return appView.abstractValueFactory().createUncheckedNullValue();
    }
    return AbstractValue.unknown();
  }

  @Override
  public DynamicType getDynamicType() {
    return DynamicType.unknown();
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.ARRAY;
  }

  @Override
  public Nullability getNullability() {
    return nullability;
  }

  @Override
  public boolean isArrayParameter() {
    return true;
  }

  @Override
  public ConcreteArrayTypeParameterState asArrayParameter() {
    return this;
  }

  public boolean isEffectivelyBottom() {
    return nullability.isBottom() && !hasInParameters();
  }

  public boolean isEffectivelyUnknown() {
    return nullability.isMaybeNull();
  }

  @Override
  public ParameterState mutableCopy() {
    return new ConcreteArrayTypeParameterState(nullability, copyInParameters());
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeParameterState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    assert parameterType.isArrayType();
    assert !nullability.isUnknown();
    nullability = nullability.join(parameterState.getNullability());
    if (nullability.isUnknown()) {
      return unknown();
    }
    boolean inParametersChanged = mutableJoinInParameters(parameterState);
    if (widenInParameters(appView)) {
      return unknown();
    }
    if (inParametersChanged) {
      onChangedAction.execute();
    }
    return this;
  }
}
