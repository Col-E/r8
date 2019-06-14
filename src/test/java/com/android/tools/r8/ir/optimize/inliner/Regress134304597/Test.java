// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.Regress134304597;

class Test {
  public int i = 33;
  private int j = 42;

  Test() {
    i = System.currentTimeMillis() == 42 ? 0 : 1;
    j = i + 3;
  }

  public boolean getValue() {
    return i > 0;
  }

  public void printValue() {
    System.out.println(j);
  }
}
