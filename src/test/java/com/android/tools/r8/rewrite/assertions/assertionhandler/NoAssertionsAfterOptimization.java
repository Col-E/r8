// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.assertionhandler;

import com.android.tools.r8.Keep;

public class NoAssertionsAfterOptimization {

  private static boolean alwaysFalse() {
    return false;
  }

  @Keep
  private static void assertionUnderAlwaysFalseCondition() {
    if (alwaysFalse()) {
      assert false : "Fifth assertion";
    }
  }

  public static void main(String[] args) {
    System.out.print("Hello, ");
    assertionUnderAlwaysFalseCondition();
    System.out.println("world!");
  }
}
