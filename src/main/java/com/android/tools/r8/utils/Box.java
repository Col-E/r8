// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Comparator;
import java.util.Objects;
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

  public void setMin(T element, Comparator<T> comparator) {
    if (!isSet() || comparator.compare(element, get()) < 0) {
      set(element);
    }
  }

  public boolean isSet() {
    return value != null;
  }

  public T getAndSet(T newValue) {
    T oldValue = value;
    value = newValue;
    return oldValue;
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    Box<?> box = (Box<?>) object;
    return Objects.equals(value, box.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public void empty() {
    value = null;
  }
}
