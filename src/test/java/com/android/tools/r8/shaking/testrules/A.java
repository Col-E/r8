// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.testrules;

public class A {

  public static int m(int a, int b) {
    int r = a + b;
    System.out.println(a + " + " + b + " = " + r);
    return r;
  }

  public static int method() {
    return m(m(m(1, 2), m(3, 4)), m(m(5, 6), m(7, 8)));
  }
}
