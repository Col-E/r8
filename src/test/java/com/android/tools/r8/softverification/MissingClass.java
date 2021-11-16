// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.softverification;

public class MissingClass {

  public static int staticField = 42;

  public int instanceField = 42;

  public static void staticMethod() {
    System.out.println("MissingClass::staticMethod");
  }

  public void instanceMethod() {
    System.out.println("MissingClass::instanceMethod");
  }
}
