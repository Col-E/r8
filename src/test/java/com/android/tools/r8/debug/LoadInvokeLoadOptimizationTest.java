// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class LoadInvokeLoadOptimizationTest {

  static void bar(int x) {
    // Left intentionally empty. Used for breaking.
  }

  void foo() {
    int x = 42; bar(x); bar(x);
  }

  public static void main(String[] args) {
    new LoadInvokeLoadOptimizationTest().foo();
  }
}
