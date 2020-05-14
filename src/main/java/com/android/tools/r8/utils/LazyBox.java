// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Supplier;

public class LazyBox<T> extends Box<T> {

  private final Supplier<T> supplier;

  public LazyBox(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  public T computeIfAbsent() {
    return super.computeIfAbsent(supplier);
  }
}
