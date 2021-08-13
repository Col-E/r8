// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class UnknownParameterState extends ParameterState {

  private static final UnknownParameterState INSTANCE = new UnknownParameterState();

  private UnknownParameterState() {}

  public static UnknownParameterState get() {
    return INSTANCE;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return AbstractValue.unknown();
  }

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ParameterState parameterState, Action onChangedAction) {
    return this;
  }
}
