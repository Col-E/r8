// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

public class SynchronizedMethodTest {

  private static synchronized int syncStatic(int x) {
    if (x % 2 == 0) {
      return 42;
    }
    return -Math.abs(x);
  }

  private synchronized int syncInstance(int x) {
    if (x % 2 == 0) {
      return 42;
    }
    return -Math.abs(x);
  }

  private static synchronized int throwing(int cond) {
    int x = 42;
    if (cond < 0) {
      throw new IllegalStateException();
    }
    return 2;
  }

  private static synchronized int monitorExitRegression(int cond) {
    int x = 42;
    switch (cond) {
      case 1:
        return 1;
      case 2:
        throw new IllegalStateException();
      case 3:
        throw new RuntimeException();
      case 4:
        return 2;
      case 5:
        x = 7;
      case 6:
        return 3;
      default:
    }
    if (cond > 0) {
      x = cond + cond;
    } else {
      throw new ArithmeticException();
    }
    return 2;
  }

  public static void main(String[] args) {
    System.out.println(syncStatic(1234));
    System.out.println(new SynchronizedMethodTest().syncInstance(1234));
    System.out.println(throwing(1234));
    System.out.println(monitorExitRegression(1234));
  }
}
