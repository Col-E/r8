// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class LocalEndTest {

  public void foo() {
    {
      int x = 42;
      try {
        bar();
        x = 7;
      } catch (Throwable e) {}
    }
    int y = 11; // Replaced by stack value of previously visible x (which must not become visible).
    System.out.println(y);
  }

  private void bar() {
    // nothing to do
  }

  public static void main(String[] args) {
    new LocalEndTest().foo();
  }
}
