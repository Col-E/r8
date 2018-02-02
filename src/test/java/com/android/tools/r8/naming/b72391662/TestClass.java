// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b72391662;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class TestClass extends Super implements Interface {
  private final int value;

  TestClass() {
    this.value = 3;
  }

  public int getValue() {
    return value;
  }

  static String staticMethod() {
    return "1";
  }

  String instanceMethod() {
    return "2";
  }

  private int y(IntSupplier x) {
    return x.getAsInt();
  }
  int x() {
    return y(super::returnFive);
  }

  Object supplyNull() {
    System.out.print("A");
    return null;
  }

  Object useSupplier(Supplier<Object> a) {
    return a.get();
  }
}
