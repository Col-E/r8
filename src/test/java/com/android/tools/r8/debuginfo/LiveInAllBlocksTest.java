// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

public class LiveInAllBlocksTest {

  public static int foo(int x) {
    if (x % 2 == 0) {
      int y;
      switch (x) {
        case 2:
          y = 1;
          break;
        default:
        case 4:
          y = 2;
          break;
        case 6:
          y = 3;
          break; // javac does not produce a line entry here.
      }
      if (x % 4 == 0) {
        if (x > 0) {
          y += 10;
        }
        if (x < 0) {
          y += -10;
        }
        if (x == 0) {
          x++;
        }
      } else {
        if (x > 0) {
          y += 20;
        }
        if (x < 0) {
          y += -20;
        }
        if (x == 0) {
          x++;
        }
      }
    }
    return x;
  }

  public static void main(String[] args) {
    System.out.print(LiveInAllBlocksTest.foo(42));
  }
}
