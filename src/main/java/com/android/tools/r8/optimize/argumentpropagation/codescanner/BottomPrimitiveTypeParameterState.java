// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomPrimitiveTypeParameterState extends BottomParameterState {

  private static final BottomPrimitiveTypeParameterState INSTANCE =
      new BottomPrimitiveTypeParameterState();

  private BottomPrimitiveTypeParameterState() {}

  public static BottomPrimitiveTypeParameterState get() {
    return INSTANCE;
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ParameterState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    if (parameterState.isBottom()) {
      assert parameterState == bottomPrimitiveTypeParameter();
      return this;
    }
    if (parameterState.isUnknown()) {
      return parameterState;
    }
    assert parameterState.isConcrete();
    assert parameterState.asConcrete().isPrimitiveParameter();
    return parameterState.mutableCopy();
  }
}
