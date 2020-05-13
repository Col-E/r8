// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Supplier;

public class Box<T> {

  private T value;

  public Box() {}

  public Box(T initialValue) {
    set(initialValue);
  }

  public T computeIfAbsent(Supplier<T> supplier) {
    if (value == null) {
      value = supplier.get();
    }
    return value;
  }

  public T get() {
    return value;
  }

  public void set(T value) {
    this.value = value;
  }

  public boolean isSet() {
    return value != null;
  }
}
