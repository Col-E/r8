// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate.a;

public class A {

  protected void foo() {
    System.out.println("A.foo");
  }

  void bar() {
    System.out.println("A.bar");
  }

  public static class B extends A {

    @Override
    protected void foo() {
      System.out.println("B.foo");
    }

    @Override
    void bar() {
      System.out.println("B.bar");
    }
  }

  public static void run(A a) {
    a.foo();
    a.bar();
  }
}
