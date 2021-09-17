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
import java.util.Collections;
import java.util.Set;

public class ConcreteReceiverParameterState extends ConcreteReferenceTypeParameterState {

  private DynamicType dynamicType;

  public ConcreteReceiverParameterState(DynamicType dynamicType) {
    this(dynamicType, Collections.emptySet());
  }

  public ConcreteReceiverParameterState(
      DynamicType dynamicType, Set<MethodParameter> inParameters) {
    super(inParameters);
    this.dynamicType = dynamicType;
    assert !isEffectivelyBottom() : "Must use BottomReceiverParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  @Override
  public ParameterState clearInParameters() {
    if (hasInParameters()) {
      if (dynamicType.isBottom()) {
        return bottomReceiverParameter();
      }
      internalClearInParameters();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return AbstractValue.unknown();
  }

  @Override
  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public Nullability getNullability() {
    return getDynamicType().getDynamicUpperBoundType().nullability();
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.RECEIVER;
  }

  public boolean isEffectivelyBottom() {
    return dynamicType.isBottom() && !hasInParameters();
  }

  public boolean isEffectivelyUnknown() {
    return dynamicType.isUnknown();
  }

  @Override
  public boolean isReceiverParameter() {
    return true;
  }

  @Override
  public ConcreteReceiverParameterState asReceiverParameter() {
    return this;
  }

  @Override
  public ParameterState mutableCopy() {
    return new ConcreteReceiverParameterState(dynamicType, copyInParameters());
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeParameterState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    // TODO(b/190154391): Always take in the static type as an argument, and unset the dynamic type
    //  if it equals the static type.
    assert parameterType == null || parameterType.isClassType();
    DynamicType oldDynamicType = dynamicType;
    dynamicType = dynamicType.join(appView, parameterState.getDynamicType());
    if (dynamicType.isUnknown()) {
      return unknown();
    }
    boolean inParametersChanged = mutableJoinInParameters(parameterState);
    if (widenInParameters(appView)) {
      return unknown();
    }
    if (!dynamicType.equals(oldDynamicType) || inParametersChanged) {
      onChangedAction.execute();
    }
    return this;
  }
}
