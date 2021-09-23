// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodStateOrUnknown;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;

public class MethodReprocessingCriteria {

  public static final MethodReprocessingCriteria ALWAYS_REPROCESS =
      new MethodReprocessingCriteria();

  private final Int2ReferenceMap<ParameterReprocessingCriteria> reproccesingCriteria;

  private MethodReprocessingCriteria() {
    this.reproccesingCriteria = new Int2ReferenceOpenHashMap<>();
  }

  public MethodReprocessingCriteria(
      Int2ReferenceMap<ParameterReprocessingCriteria> reproccesingCriteria) {
    assert !reproccesingCriteria.isEmpty();
    this.reproccesingCriteria = reproccesingCriteria;
  }

  public static MethodReprocessingCriteria alwaysReprocess() {
    return ALWAYS_REPROCESS;
  }

  public ParameterReprocessingCriteria getParameterReprocessingCriteria(int parameterIndex) {
    return reproccesingCriteria.getOrDefault(
        parameterIndex, ParameterReprocessingCriteria.alwaysReprocess());
  }

  public ConcreteMonomorphicMethodStateOrUnknown widenMethodState(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      ConcreteMonomorphicMethodState methodState) {
    for (int parameterIndex = 0; parameterIndex < methodState.size(); parameterIndex++) {
      ParameterState parameterState = methodState.getParameterState(parameterIndex);
      assert !parameterState.isBottom();
      if (parameterState.isUnknown()) {
        continue;
      }

      if (parameterState.getAbstractValue(appView).isSingleValue()) {
        // Don't widen when we have information that can be used for parameter removal.
        continue;
      }

      ParameterReprocessingCriteria parameterReprocessingCriteria =
          getParameterReprocessingCriteria(parameterIndex);
      DexType parameterType = method.getArgumentType(parameterIndex);
      if (parameterReprocessingCriteria.shouldReprocess(
          appView, parameterState.asConcrete(), parameterType)) {
        continue;
      }

      methodState.setParameterState(parameterIndex, UnknownParameterState.get());
    }

    return Iterables.all(methodState.getParameterStates(), ParameterState::isUnknown)
        ? MethodState.unknown()
        : methodState;
  }
}
