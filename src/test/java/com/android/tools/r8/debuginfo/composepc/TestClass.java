// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.composepc;

class TestClass {

  public static void foo() {
    System.out.println("AAA");
  }

  public static void bar() {
    System.out.println("BBB");
    if (System.nanoTime() > 0) {
      throw new RuntimeException(); // LINE 15 - update ComposePcEncodingTest if changed.
    }
  }

  public static void baz() {
    System.out.println("CCC");
  }

  public static void main(String[] args) {
    foo();
    bar(); // LINE 25 - update ComposePcEncodingTest if changed.
    baz();
  }

  // Line removed by transform.
  public static void unusedKeptAndNoLineInfo() {
    System.out.println("DDDD");
  }
}
