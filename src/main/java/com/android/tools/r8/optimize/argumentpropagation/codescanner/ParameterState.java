// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public abstract class ParameterState {

  public static UnknownParameterState unknown() {
    return UnknownParameterState.get();
  }

  public abstract AbstractValue getAbstractValue();

  public boolean isConcrete() {
    return false;
  }

  public ConcreteParameterState asConcrete() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }

  public final ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ParameterState parameterState) {
    return mutableJoin(appView, parameterState, Action.empty());
  }

  public abstract ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ParameterState parameterState, Action onChangedAction);
}
