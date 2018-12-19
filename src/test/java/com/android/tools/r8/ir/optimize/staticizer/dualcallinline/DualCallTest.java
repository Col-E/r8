// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.dualcallinline;


public class DualCallTest {
  private static Candidate INSTANCE = new Candidate();

  private static void calledTwice() {
    System.out.println(INSTANCE.foo());
  }

  public static void main(String[] args) {
    calledTwice();
    calledTwice();
  }
}
