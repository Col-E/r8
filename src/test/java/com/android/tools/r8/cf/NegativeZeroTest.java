// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

public class NegativeZeroTest {

  public static void main(String[] args) {
    System.out.println(-0.0f);
    System.out.println(Float.floatToIntBits(-0.0f));
    System.out.println(Float.floatToIntBits(0.0f));
    if (Float.floatToIntBits(-0.0f) == Float.floatToIntBits(0.0f)) {
      throw new AssertionError("Negative float not preserved");
    }
    if (Double.doubleToLongBits(-0.0) == Double.doubleToLongBits(0.0)) {
      throw new AssertionError("Negative double not preserved");
    }
  }
}
