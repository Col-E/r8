// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;

public class BottomMethodState extends MethodStateBase
    implements ConcreteMonomorphicMethodStateOrBottom, ConcretePolymorphicMethodStateOrBottom {

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
  public ConcreteMonomorphicMethodStateOrBottom asMonomorphicOrBottom() {
    return this;
  }

  @Override
  public ConcretePolymorphicMethodStateOrBottom asPolymorphicOrBottom() {
    return this;
  }

  @Override
  public MethodState mutableCopy() {
    return this;
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      MethodState methodState) {
    return methodState.mutableCopy();
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      Function<MethodState, MethodState> methodStateSupplier) {
    return mutableJoin(appView, methodSignature, methodStateSupplier.apply(this));
  }
}
