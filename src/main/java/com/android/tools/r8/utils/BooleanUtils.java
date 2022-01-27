// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

public class BooleanUtils {

  private static final Boolean[] VALUES = new Boolean[] { Boolean.TRUE, Boolean.FALSE };
  private static final Boolean[] TRUE_VALUES = new Boolean[] {Boolean.TRUE};
  private static final Boolean[] FALSE_VALUES = new Boolean[] {Boolean.FALSE};

  public static int intValue(boolean value) {
    return value ? 1 : 0;
  }

  public static long longValue(boolean value) {
    return value ? 1L : 0L;
  }

  public static Boolean[] values() {
    return VALUES;
  }

  public static Boolean[] trueValues() {
    return TRUE_VALUES;
  }

  public static Boolean[] falseValues() {
    return FALSE_VALUES;
  }

  public static boolean xor(boolean x, boolean y) {
    return (x && !y) || (!x && y);
  }
}
