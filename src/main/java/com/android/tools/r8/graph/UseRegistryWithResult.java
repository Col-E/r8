// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public abstract class UseRegistryWithResult<R, T extends Definition> extends UseRegistry<T> {

  private R result;

  public UseRegistryWithResult(AppView<?> appView, T context) {
    super(appView, context);
  }

  public UseRegistryWithResult(AppView<?> appView, T context, R defaultResult) {
    super(appView, context);
    this.result = defaultResult;
  }

  public R getResult() {
    return result;
  }

  public void setResult(R result) {
    this.result = result;
    doBreak();
  }
}
