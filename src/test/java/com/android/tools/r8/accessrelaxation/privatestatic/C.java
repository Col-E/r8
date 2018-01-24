// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation.privatestatic;

public class C extends B {
  public String foo() {
    return "B::foo()" + super.foo();
  }

  public String blah(int i) {
    return "C::blah(int)";
  }

  public String bar(int i) {
    try {
      return "C::bar(int)" + super.bar(i) + super.bar() + super.bar();
    } catch (Throwable e) {
      return "C::bar(int)" + e.getClass().getName() + super.bar() + super.bar();
    }
  }

  public void dump() {
    System.out.println(this.bar());
    System.out.println(this.bar(0));
    System.out.println(this.foo());
    System.out.println(this.blah(0));
  }

  public static void main(String[] args) {
    System.out.println(A.pBaz());
    System.out.println(A.pBar());
    System.out.println(A.pBar1());
    System.out.println(A.pBlah1());

    System.out.println(B.pBlah1());
    System.out.println(BB.pBlah1());

    new A().dump();
    new B().dump();
    new BB().dump();
    new C().dump();
  }
}
