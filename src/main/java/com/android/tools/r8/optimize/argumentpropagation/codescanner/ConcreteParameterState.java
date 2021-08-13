// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;

public abstract class ConcreteParameterState extends ParameterState {

  enum ConcreteParameterStateKind {
    ARRAY,
    CLASS,
    PRIMITIVE,
    RECEIVER
  }

  private Set<MethodParameter> inParameters;

  ConcreteParameterState() {
    this.inParameters = Collections.emptySet();
  }

  ConcreteParameterState(MethodParameter inParameter) {
    this.inParameters = SetUtils.newHashSet(inParameter);
  }

  public void clearInParameters() {
    inParameters.clear();
  }

  public boolean hasInParameters() {
    return !inParameters.isEmpty();
  }

  public Set<MethodParameter> getInParameters() {
    return inParameters;
  }

  public abstract ConcreteParameterStateKind getKind();

  public boolean isArrayParameter() {
    return false;
  }

  public ConcreteArrayTypeParameterState asArrayParameter() {
    return null;
  }

  public boolean isClassParameter() {
    return false;
  }

  public ConcreteClassTypeParameterState asClassParameter() {
    return null;
  }

  public boolean isPrimitiveParameter() {
    return false;
  }

  public ConcretePrimitiveTypeParameterState asPrimitiveParameter() {
    return null;
  }

  public boolean isReceiverParameter() {
    return false;
  }

  public ConcreteReceiverParameterState asReceiverParameter() {
    return null;
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteParameterState asConcrete() {
    return this;
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ParameterState parameterState, Action onChangedAction) {
    if (parameterState.isUnknown()) {
      return parameterState;
    }
    ConcreteParameterStateKind kind = getKind();
    ConcreteParameterStateKind otherKind = parameterState.asConcrete().getKind();
    if (kind == otherKind) {
      switch (getKind()) {
        case ARRAY:
          return asArrayParameter()
              .mutableJoin(parameterState.asConcrete().asArrayParameter(), onChangedAction);
        case CLASS:
          return asClassParameter()
              .mutableJoin(
                  appView, parameterState.asConcrete().asClassParameter(), onChangedAction);
        case PRIMITIVE:
          return asPrimitiveParameter()
              .mutableJoin(
                  appView, parameterState.asConcrete().asPrimitiveParameter(), onChangedAction);
        case RECEIVER:
          return asReceiverParameter()
              .mutableJoin(parameterState.asConcrete().asReceiverParameter(), onChangedAction);
        default:
          // Dead.
      }
    }

    assert false;
    return unknown();
  }

  boolean mutableJoinInParameters(ConcreteParameterState parameterState) {
    if (parameterState.inParameters.isEmpty()) {
      return false;
    }
    if (inParameters.isEmpty()) {
      assert inParameters == Collections.<MethodParameter>emptySet();
      inParameters = Sets.newIdentityHashSet();
    }
    return inParameters.addAll(parameterState.inParameters);
  }

  boolean widenInParameters() {
    // TODO(b/190154391): Widen to unknown when the size of the collection exceeds a threshold.
    return false;
  }
}
