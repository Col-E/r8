// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.movestringconstants;

public class TestClass {
  static void foo(String arg1, String arg2, String arg3, String arg4) {
    Utils.check(arg1, "StringConstants::foo#1");
    Utils.check("", "StringConstants::foo#2");
    if (arg2.length() == 12345) {
      Utils.check(null, "StringConstants::foo#3");
    }
    try {
      Utils.check(arg3, "StringConstants::foo#4");
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    try {
      Utils.check(arg4, "StringConstants::foo#5");
    } finally {
      System.out.println("finally");
    }
  }
}

class Utils {
  static void check(Object value, String message) {
    if (value == null) {
      throwException(message);
    }
  }

  private synchronized static void throwException(String message) {
    throw new RuntimeException(message);
  }
}
