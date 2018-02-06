// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class LocalChangeOnSameLineTest {
  int i = 0;

  int bar() {
    System.out.println("bar call " + ++i);
    return i;
  }

  void foo() {
    { int x = bar(); int y = bar(); }
    { int x = bar(); int y = bar(); }
  }

  public static void main(String[] args) {
    new LocalChangeOnSameLineTest().foo();
  }
}
