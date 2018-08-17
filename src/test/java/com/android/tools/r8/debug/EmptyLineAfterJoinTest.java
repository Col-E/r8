// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class EmptyLineAfterJoinTest {

  public void foo() {
    {
      int x = 42;
      try {
        bar();
        x = 7;
      } catch (Throwable e) {} // Line set to be 16 in dump file.
    }
    int y = 7; // Replaced by nop in dump to test we still can hit line 16.
    System.out.println(y);
  }

  private void bar() {
    // nothing to do
  }

  public static void main(String[] args) {
    new EmptyLineAfterJoinTest().foo();
  }
}
