// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class PostIncrementTest {

  static final int LENGTH = 4 * 16;

  private static void loop(int[] a) {
    int i = 0;
    int s = 128;
    while (i++ < LENGTH - 2) {
      if (i % 2 == 0) {
        a[i] = s++;
      }
    }
  }

  public static void main(String[] args) {
    loop(new int[LENGTH]);
  }
}
