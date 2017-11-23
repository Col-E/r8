// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

public class TestMainWithClassForName {
  private static void method1() throws Exception {
    System.out.println(Class.forName(
        "com.android.tools.r8.shaking.forceproguardcompatibility.TestClassWithoutDefaultConstructor"));
  }

  public static void main(String[] args) throws Exception {
    System.out.println(Class.forName(
        "com.android.tools.r8.shaking.forceproguardcompatibility.TestClassWithDefaultConstructor"));
    TestMainWithClassForName.method1();
  }
}
