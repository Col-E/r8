// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public abstract class UseRegistryWithResult<T> extends UseRegistry {

  private T result;

  public UseRegistryWithResult(DexItemFactory factory) {
    super(factory);
  }

  public UseRegistryWithResult(DexItemFactory factory, T defaultResult) {
    super(factory);
    this.result = defaultResult;
  }

  public T getResult() {
    return result;
  }

  public void setResult(T result) {
    this.result = result;
    doBreak();
  }
}
