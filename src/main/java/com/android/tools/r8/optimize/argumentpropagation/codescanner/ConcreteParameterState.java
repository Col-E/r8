// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class ConcreteParameterState extends NonEmptyParameterState {

  public enum ConcreteParameterStateKind {
    ARRAY,
    CLASS,
    PRIMITIVE,
    RECEIVER
  }

  private Set<MethodParameter> inParameters;

  ConcreteParameterState(Set<MethodParameter> inParameters) {
    this.inParameters = inParameters;
  }

  public abstract ParameterState clearInParameters();

  void internalClearInParameters() {
    inParameters = Collections.emptySet();
  }

  public Set<MethodParameter> copyInParameters() {
    if (inParameters.isEmpty()) {
      assert inParameters == Collections.<MethodParameter>emptySet();
      return inParameters;
    }
    return new HashSet<>(inParameters);
  }

  public boolean hasInParameters() {
    return !inParameters.isEmpty();
  }

  public Set<MethodParameter> getInParameters() {
    assert inParameters.isEmpty() || inParameters instanceof HashSet<?>;
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

  public boolean isReferenceParameter() {
    return false;
  }

  public ConcreteReferenceTypeParameterState asReferenceParameter() {
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
  public final ParameterState mutableJoin(
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
    ConcreteParameterState concreteParameterState = parameterState.asConcrete();
    if (isReferenceParameter()) {
      assert concreteParameterState.isReferenceParameter();
      return asReferenceParameter()
          .mutableJoin(
              appView,
              concreteParameterState.asReferenceParameter(),
              parameterType,
              onChangedAction);
    }
    return asPrimitiveParameter()
        .mutableJoin(
            appView, concreteParameterState.asPrimitiveParameter(), parameterType, onChangedAction);
  }

  boolean mutableJoinInParameters(ConcreteParameterState parameterState) {
    if (parameterState.inParameters.isEmpty()) {
      return false;
    }
    if (inParameters.isEmpty()) {
      assert inParameters == Collections.<MethodParameter>emptySet();
      inParameters = new HashSet<>();
    }
    return inParameters.addAll(parameterState.inParameters);
  }

  /**
   * Returns true if the in-parameters set should be widened to unknown, in which case the entire
   * parameter state must be widened to unknown.
   */
  boolean widenInParameters(AppView<AppInfoWithLiveness> appView) {
    return inParameters != null
        && inParameters.size()
            > appView.options().callSiteOptimizationOptions().getMaxNumberOfInParameters();
  }
}
