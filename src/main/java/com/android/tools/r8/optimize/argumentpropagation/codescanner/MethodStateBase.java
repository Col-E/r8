// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

public abstract class MethodStateBase implements MethodState {

  public static BottomMethodState bottom() {
    return BottomMethodState.get();
  }

  public static UnknownMethodState unknown() {
    return UnknownMethodState.get();
  }

  @Override
  public boolean isBottom() {
    return false;
  }

  @Override
  public boolean isConcrete() {
    return false;
  }

  @Override
  public ConcreteMethodState asConcrete() {
    return null;
  }

  @Override
  public boolean isMonomorphic() {
    return false;
  }

  @Override
  public ConcreteMonomorphicMethodState asMonomorphic() {
    return null;
  }

  @Override
  public boolean isUnknown() {
    return false;
  }
}
