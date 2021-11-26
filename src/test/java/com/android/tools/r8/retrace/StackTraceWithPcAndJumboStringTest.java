// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

public class StackTraceWithPcAndJumboStringTest {

  public static void foo() {
    if ((System.nanoTime() > 0 ? "SomeString" : "AndSomeOtherString").length() == 10) {
      throw new RuntimeException("MoreStrings" + System.nanoTime() + "That Other String");
    }
  }

  public static void bar() {
    foo();
  }

  public static void main(String[] args) {
    bar();
  }
}
