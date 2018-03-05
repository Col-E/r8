// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

public class CallLoopTest {
  private static boolean doThrow = false;
  private static boolean doLoop = true;

  public static void main(String[] args) {
    if (args.length % 2 == 0) doThrow = true;
    if (args.length % 3 == 0) doLoop = false;
    loop1();
    try {
      loop2();
    } catch (RuntimeException e) {
      // OK
    }
  }

  private static void loop1() {
    while (doLoop) {}
  }

  private static void loop2() {
    while (true) {
      maybeThrow();
    }
  }

  private static void maybeThrow() {
    if (doThrow) {
      throw new RuntimeException();
    }
  }
}
