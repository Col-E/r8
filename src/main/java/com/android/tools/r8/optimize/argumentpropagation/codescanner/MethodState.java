// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;

public interface MethodState {

  static BottomMethodState bottom() {
    return BottomMethodState.get();
  }

  static UnknownMethodState unknown() {
    return UnknownMethodState.get();
  }

  boolean isBottom();

  boolean isConcrete();

  ConcreteMethodState asConcrete();

  boolean isMonomorphic();

  ConcreteMonomorphicMethodState asMonomorphic();

  ConcreteMonomorphicMethodStateOrBottom asMonomorphicOrBottom();

  boolean isPolymorphic();

  ConcretePolymorphicMethodState asPolymorphic();

  ConcretePolymorphicMethodStateOrBottom asPolymorphicOrBottom();

  boolean isUnknown();

  MethodState mutableCopy();

  MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      MethodState methodState,
      StateCloner cloner);

  MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      Function<MethodState, MethodState> methodStateSupplier,
      StateCloner cloner);
}
