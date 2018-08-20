// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

public class InlineCmpDoubleTest {
  public static void main(String[] args) {
   inlinee(42);
  }

  public static void inlinee(int x) {
    inlineMe(x + 41);
  }

  public static int inlineMe(int x) {
    double a = x / 255.0;
    return a < 64.0 ? 42 : 43;
  }
}
