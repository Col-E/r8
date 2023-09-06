// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class BoxBase<T> {

  private T value;

  public BoxBase() {}

  public BoxBase(T initialValue) {
    set(initialValue);
  }

  void accept(Consumer<? super T> consumer) {
    if (isSet()) {
      consumer.accept(get());
    }
  }

  void clear() {
    set(null);
  }

  T computeIfAbsent(Supplier<T> supplier) {
    if (!isSet()) {
      set(supplier.get());
    }
    return value;
  }

  T get() {
    return value;
  }

  T getAndSet(T newValue) {
    T oldValue = value;
    value = newValue;
    return oldValue;
  }

  public T getAndCompute(Function<T, T> newValue) {
    T t = get();
    set(newValue.apply(t));
    return t;
  }

  void set(T value) {
    this.value = value;
  }

  void setMin(T value, Comparator<T> comparator) {
    if (!isSet() || comparator.compare(value, get()) < 0) {
      set(value);
    }
  }

  boolean test(Predicate<T> predicate) {
    return isSet() && predicate.test(get());
  }

  public boolean isSet() {
    return value != null;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    BoxBase<?> box = (BoxBase<?>) object;
    return Objects.equals(value, box.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
