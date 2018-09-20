// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

class StringLengthDebugTest {
  public static void main(String[] args) {
    String x = "ABC";
    int l1 = x.length();
    System.out.println(l1);
    int l2 = "XYZ".length();
    System.out.println(l2);
  }
}
