// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;

public class ConcreteMonomorphicMethodState extends ConcreteMethodState
    implements ConcreteMonomorphicMethodStateOrBottom, ConcreteMonomorphicMethodStateOrUnknown {

  List<ParameterState> parameterStates;

  public ConcreteMonomorphicMethodState(List<ParameterState> parameterStates) {
    assert Streams.stream(Iterables.skip(parameterStates, 1))
        .noneMatch(x -> x.isConcrete() && x.asConcrete().isReceiverParameter());
    assert Iterables.any(parameterStates, parameterState -> !parameterState.isUnknown())
        : "Must use UnknownMethodState instead";
    this.parameterStates = parameterStates;
  }

  public ParameterState getParameterState(int index) {
    return parameterStates.get(index);
  }

  public List<ParameterState> getParameterStates() {
    return parameterStates;
  }

  @Override
  public ConcreteMonomorphicMethodState mutableCopy() {
    List<ParameterState> copiedParametersStates = new ArrayList<>(size());
    for (ParameterState parameterState : getParameterStates()) {
      copiedParametersStates.add(parameterState.mutableCopy());
    }
    return new ConcreteMonomorphicMethodState(copiedParametersStates);
  }

  public ConcreteMonomorphicMethodStateOrUnknown mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      ConcreteMonomorphicMethodState methodState) {
    if (size() != methodState.size()) {
      assert false;
      return unknown();
    }

    int argumentIndex = 0;
    if (size() > methodSignature.getArity()) {
      assert size() == methodSignature.getArity() + 1;
      ParameterState parameterState = parameterStates.get(0);
      ParameterState otherParameterState = methodState.parameterStates.get(0);
      DexType parameterType = null;
      parameterStates.set(
          0, parameterState.mutableJoin(appView, otherParameterState, parameterType));
      argumentIndex++;
    }

    for (int parameterIndex = 0; argumentIndex < size(); argumentIndex++, parameterIndex++) {
      ParameterState parameterState = parameterStates.get(argumentIndex);
      ParameterState otherParameterState = methodState.parameterStates.get(argumentIndex);
      DexType parameterType = methodSignature.getParameter(parameterIndex);
      parameterStates.set(
          argumentIndex, parameterState.mutableJoin(appView, otherParameterState, parameterType));
      assert !parameterStates.get(argumentIndex).isConcrete()
          || !parameterStates.get(argumentIndex).asConcrete().isReceiverParameter();
    }

    if (Iterables.all(parameterStates, ParameterState::isUnknown)) {
      return unknown();
    }
    return this;
  }

  @Override
  public boolean isMonomorphic() {
    return true;
  }

  @Override
  public ConcreteMonomorphicMethodState asMonomorphic() {
    return this;
  }

  @Override
  public ConcreteMonomorphicMethodStateOrBottom asMonomorphicOrBottom() {
    return this;
  }

  public void setParameterState(int index, ParameterState parameterState) {
    assert index == 0
        || !parameterState.isConcrete()
        || !parameterState.asConcrete().isReceiverParameter();
    parameterStates.set(index, parameterState);
  }

  public int size() {
    return parameterStates.size();
  }
}
