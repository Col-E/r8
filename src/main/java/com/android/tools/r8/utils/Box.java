// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Comparator;
import java.util.function.Supplier;

public class Box<T> extends BoxBase<T> {

  public Box() {}

  public Box(T initialValue) {
    super(initialValue);
  }

  @Override
  public void clear() {
    super.clear();
  }

  @Override
  public T computeIfAbsent(Supplier<T> supplier) {
    return super.computeIfAbsent(supplier);
  }

  @Override
  public T get() {
    return super.get();
  }

  @Override
  public T getAndSet(T newValue) {
    return super.getAndSet(newValue);
  }

  @Override
  public void set(T value) {
    super.set(value);
  }

  @Override
  public void setMin(T value, Comparator<T> comparator) {
    super.setMin(value, comparator);
  }
}
