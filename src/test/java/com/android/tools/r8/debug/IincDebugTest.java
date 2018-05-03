// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

public class IincDebugTest {

  public static void main(String[] args) {
    int j;
    {
      int i = 1;
      j = i + 1;
    }
    System.out.println(j);
  }
}
