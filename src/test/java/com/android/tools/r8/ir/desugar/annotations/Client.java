// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.annotations;

public class Client {
  public static void main(String[] args) {
    A a = new A().method();
    A b = new B().method();
    A c = new C().method();
    D d = ((D) new F()).method();
    D e = ((E) new F()).method();
    D f = new F().method();

    System.out.println("a=" + a.getClass().getSimpleName());
    System.out.println("b=" + b.getClass().getSimpleName());
    System.out.println("c=" + c.getClass().getSimpleName());
    System.out.println("d=" + d.getClass().getSimpleName());
    System.out.println("e=" + e.getClass().getSimpleName());
    System.out.println("f=" + f.getClass().getSimpleName());
  }
}
