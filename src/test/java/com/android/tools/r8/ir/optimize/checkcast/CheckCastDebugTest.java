// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import com.android.tools.r8.NeverInline;

class A {
  @NeverInline
  @Override
  public String toString() {
    return "A";
  }
}

class B extends A {
  @NeverInline
  @Override
  public String toString() {
    return super.toString() + "B";
  }
}

class C extends B {
  @NeverInline
  @Override
  public String toString() {
    return super.toString() + "C";
  }
}

class CheckCastDebugTest {
  @NeverInline
  static void differentLocals() {
    Object obj = new C();
    A a = (A) obj;
    B b = (B) a;
    C c = (C) b;
    System.out.println(c.toString());
  }

  @NeverInline
  static void sameLocal() {
    Object obj = new C();
    obj = (A) obj;
    obj = (B) obj;
    obj = (C) obj;
    System.out.println(obj.toString());
  }

  public static void main(String[] args) {
    differentLocals();
    sameLocal();
  }
}
