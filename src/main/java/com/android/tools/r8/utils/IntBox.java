// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class IntBox {

  private int value;

  public IntBox() {}

  public IntBox(int initialValue) {
    set(initialValue);
  }

  public void decrement(int i) {
    assert i > 0;
    value -= i;
  }

  public int decrementAndGet(int i) {
    decrement(i);
    return get();
  }

  public int get() {
    return value;
  }

  public int getAndIncrement() {
    return getAndIncrement(1);
  }

  public int getAndIncrement(int i) {
    int previous = value;
    increment(i);
    return previous;
  }

  public int getAndSet(int value) {
    int previous = this.value;
    set(value);
    return previous;
  }

  public void increment() {
    increment(1);
  }

  public void increment(int i) {
    assert i >= 0;
    value += i;
  }

  public int incrementAndGet() {
    return incrementAndGet(1);
  }

  public int incrementAndGet(int i) {
    increment(i);
    return get();
  }

  public void set(int value) {
    this.value = value;
  }

  public void setMax(int value) {
    if (value > get()) {
      set(value);
    }
  }
}
