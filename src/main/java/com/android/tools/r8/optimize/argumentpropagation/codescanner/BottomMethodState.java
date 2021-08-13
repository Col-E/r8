// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Supplier;

public class BottomMethodState extends MethodStateBase {

  private static final BottomMethodState INSTANCE = new BottomMethodState();

  private BottomMethodState() {}

  public static BottomMethodState get() {
    return INSTANCE;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public MethodState mutableJoin(AppView<AppInfoWithLiveness> appView, MethodState methodState) {
    return methodState;
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView, Supplier<MethodState> methodStateSupplier) {
    return methodStateSupplier.get();
  }
}
