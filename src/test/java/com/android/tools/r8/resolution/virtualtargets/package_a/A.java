// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets.package_a;

public class A {

  void foo() {
    System.out.println("A.foo");
  }

  protected void bar() {
    System.out.println("A.bar");
  }

  public static void run(A a) {
    a.foo();
    a.bar();
  }
}
