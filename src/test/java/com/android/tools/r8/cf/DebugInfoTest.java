// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

public class DebugInfoTest {

  public static void main(String[] args) {
    if (args.length > 0) {
      arg = args.length % 2 == 0;
      DebugInfoTest.method();
    }
  }

  private static boolean arg;

  private static void method() {
    int intVar;
    if (arg) {
      float floatVar1 = 0f;
      intVar = (int) floatVar1;
    } else {
      float floatVar2 = 0f;
      intVar = (int) floatVar2;
    }
  }
}
