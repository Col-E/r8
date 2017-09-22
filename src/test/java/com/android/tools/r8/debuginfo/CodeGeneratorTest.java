// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

public class CodeGeneratorTest {

  public static int intAddition(int a, int b, int c) {
    a += b;
    b += c;
    c = a + b;
    return c;
  }

  public static void main(String[] args) {
    System.out.print(intAddition(1, 2, 6));
  }
}
