// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class BreakInOneLineFinallyTest {
  int i = 0;

  void foo() {
    try { bar(); }
    finally { baz(); } // Java will fail to break here on exceptional exit.
  }

  int bar() {
    if (i++ % 2 == 0) {
      System.out.println("bar return " + i);
      return i;
    }
    System.out.println("bar throw " + i);
    throw new RuntimeException("" + i);
  }

  void baz() {
    System.out.println("baz");
  }

  public static void main(String[] args) {
    BreakInOneLineFinallyTest test = new BreakInOneLineFinallyTest();
    test.foo();
    try {
      test.foo();
    } catch (RuntimeException e) {
      System.out.println("Caught expected exception: " + e.getMessage());
      return;
    }
    throw new RuntimeException("Test failed...");
  }
}
