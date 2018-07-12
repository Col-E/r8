// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

public class ClassWithAssertions {
  int x = 0;

  ClassWithAssertions(int x) {
    this.x = x;
  }

  boolean condition() {
    return x == 1;
  }

  int getX() {
    System.out.println("1");
    assert condition();
    System.out.println("2");
    return x;
  }

  public static void main(String[] args) {
    new ClassWithAssertions(Integer.parseInt(args[0])).getX();
  }
}
