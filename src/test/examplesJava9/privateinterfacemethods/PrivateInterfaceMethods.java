// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package privateinterfacemethods;

public class PrivateInterfaceMethods {

  public static void main(String[] args) {
    System.out.println("1: " + I.DEFAULT.dFoo());
    System.out.println("2: " + I.DEFAULT.lFoo());
    System.out.println("3: " + I.xFoo());
    System.out.println("4: " + new C().dFoo());
  }
}

class C implements I {

  public String dFoo() {
    return "c>" + I.super.dFoo();
  }
}

interface IB {

  String dFoo();
}

interface I {

  I DEFAULT = new I() {{
    System.out.println("0: " + sFoo(false, this));
  }};

  static String xFoo() {
    return "x>" + sFoo(true, null);
  }

  private static String sFoo(boolean simple, I it) {
    return simple ? "s"
        : ("s>" + it.iFoo(true) + ">" + new I() {
          public String dFoo() {
            return "a";
          }
        }.dFoo());
  }

  private String iFoo(boolean skip) {
    return skip ? "i" : ("i>" + sFoo(false, this));
  }

  default String dFoo() {
    return "d>" + iFoo(false);
  }

  default String lFoo() {
    IB ib = () -> "l>" + iFoo(false);
    return ib.dFoo();
  }
}
