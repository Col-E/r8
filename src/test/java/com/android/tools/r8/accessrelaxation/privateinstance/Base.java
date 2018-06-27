// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

public class Base {

  // NOTE: here and below 'synchronized' is supposed to disable inlining of this method.
  private synchronized String foo() {
    return "Base::foo()";
  }

  public String pFoo() {
    return foo();
  }

  private synchronized String foo1() {
    return "Base::foo1()";
  }

  public String pFoo1() {
    return foo1();
  }

  private synchronized String foo2() {
    return "Base::foo2()";
  }

  public String pFoo2() {
    return foo2();
  }

  private synchronized String bar1(int i) {
    throw new AssertionError("Sub1#bar1(int) will not use this signature.");
  }

  public void dump() {
    System.out.println(foo());
    System.out.println(foo1());
    System.out.println(foo2());
    try {
      bar1(0);
    } catch (AssertionError e) {
      // expected
    }
  }

}
