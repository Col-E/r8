// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Supplier;

public abstract class ConcreteMethodState extends MethodStateBase {

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteMethodState asConcrete() {
    return this;
  }

  public boolean isPolymorphic() {
    return false;
  }

  public ConcretePolymorphicMethodState asPolymorphic() {
    return null;
  }

  @Override
  public MethodState mutableJoin(AppView<AppInfoWithLiveness> appView, MethodState methodState) {
    if (methodState.isBottom()) {
      return this;
    }
    if (methodState.isUnknown()) {
      return methodState;
    }
    return mutableJoin(appView, methodState.asConcrete());
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView, Supplier<MethodState> methodStateSupplier) {
    return mutableJoin(appView, methodStateSupplier.get());
  }

  private MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ConcreteMethodState methodState) {
    if (isMonomorphic() && methodState.isMonomorphic()) {
      return asMonomorphic().mutableJoin(appView, methodState.asMonomorphic());
    }
    if (isPolymorphic() && methodState.isPolymorphic()) {
      return asPolymorphic().mutableJoin(appView, methodState.asPolymorphic());
    }
    assert false;
    return unknown();
  }
}
