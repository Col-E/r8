// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class NonExitingMethodTest {

  private static int bValue = 0;

  private static int b() {
    if (bValue == 1) throw new RuntimeException();
    return ++bValue;
  }

  public void foo(int arg) {
    int x = 1;
    int y = 2;
    while (true) {
      int z = b();
      x = y;
      y = z;
    }
  }

  public static void main(String[] args) {
    try {
      new NonExitingMethodTest().foo(42);
    } catch (RuntimeException e) {
      return;
    }
    throw new RuntimeException("Expected exception...");
  }
}
