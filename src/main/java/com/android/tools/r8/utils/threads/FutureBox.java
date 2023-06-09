// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import com.android.tools.r8.errors.Unimplemented;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FutureBox<T> implements Future<T> {

  private final T value;

  public FutureBox(T value) {
    this.value = value;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    throw new Unimplemented();
  }

  @Override
  public boolean isCancelled() {
    throw new Unimplemented();
  }

  @Override
  public boolean isDone() {
    throw new Unimplemented();
  }

  @Override
  public T get() {
    return value;
  }

  @Override
  public T get(long timeout, TimeUnit unit) {
    return value;
  }
}
