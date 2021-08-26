// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;

// Use this when the nothing is known.
public class UnknownMethodState extends MethodStateBase
    implements ConcreteMonomorphicMethodStateOrUnknown, ConcretePolymorphicMethodStateOrUnknown {

  private static final UnknownMethodState INSTANCE = new UnknownMethodState();

  private UnknownMethodState() {}

  public static UnknownMethodState get() {
    return INSTANCE;
  }

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public UnknownMethodState mutableCopy() {
    return this;
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      MethodState methodState) {
    return this;
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      Function<MethodState, MethodState> methodStateSupplier) {
    return this;
  }
}
