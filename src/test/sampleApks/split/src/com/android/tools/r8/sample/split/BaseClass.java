// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.split;


public class BaseClass {

  int initialValue;

  public BaseClass(int initialValue) {
    this.initialValue = initialValue;
  }

  public int calculate(int x) {
    int result = 2;
    for (int i = 0; i < 42; i++) {
      result += initialValue + x;
    }
    return result;
  }

   public int largeMethod(int x, int y) {
    int a = x + y;
    int b;
    int c;
    double d;
    String s;
    if (a < 22) {
      b = a * 42 / y;
      c = b - 80;
      d = a + b + c * y - x * a;
      s = "foobar";
    } else if (a < 42) {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "foo";
    } else {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "bar";
    }
    if (s.equals("bar")) {
      d = 49;
      s += b;
    } else {
      b = 63;
      s = "barbar" + b;
    }

    if (a < 22) {
      b = a * 42 / y;
      c = b - 80;
      d = a + b + c * y - x * a;
      s = "foobar";
    } else if (a < 42) {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "foo";
    } else {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "bar";
    }
    if (s.equals("bar")) {
      d = 49;
      s += b;
    } else {
      b = 63;
      s = "barbar" + b;
    }

    if (a < 22) {
      b = a * 42 / y;
      c = b - 80;
      d = a + b + c * y - x * a;
      s = "foobar";
    } else if (a < 42) {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "foo";
    } else {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "bar";
    }
    if (s.equals("bar")) {
      d = 49;
      s += b;
    } else {
      b = 63;
      s = "barbar" + b;
    }

    return a + b - c * x;
  }
}
