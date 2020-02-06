// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

public class DexPcWithDebugInfoForOverloadedMethodsTest {

  private static void inlinee(String message) {
    if (System.currentTimeMillis() > 0) {
      throw new RuntimeException(message);
    }
  }

  public static void overloaded(int x) {
    inlinee("overloaded(int)" + x);
  }

  public static void overloaded(String y) {
    inlinee("overloaded(String)" + y);
  }

  public static void main(String[] args) {
    if (args.length > 0) {
      overloaded(42);
    } else {
      overloaded("42");
    }
  }
}
