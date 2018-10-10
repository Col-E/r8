// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.release;

public class ShareCommonCodeOnDistinctPositionsTest {

  public static void main(String[] args) {
    int x;
    int len = args.length;
    if (len > 42) {
      x = (len - 2) + len * 2;
    } else {
      x = (len - 2) + len * 2;
    }
    System.out.println(x);
  }
}
