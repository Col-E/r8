// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.assertionhandler;

import com.android.tools.r8.Keep;

public class AssertionsWithConditions {

  @Keep
  public static void assertionWithSimpleCondition(int x) {
    assert x < 0;
  }

  private static boolean isZero(int x) {
    return x == 0;
  }

  private static boolean isNegative(int x) {
    return x < 0;
  }

  @Keep
  public static void assertionWithCondition(int x, int y, int z) {
    assert x == 0 && isZero(y) && isNegative(z);
  }

  public static void main(String[] args) {
    assertionWithSimpleCondition(args.length);
    assertionWithCondition(args.length, args.length, args.length);
  }
}
