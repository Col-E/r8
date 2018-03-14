// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

class EmptyMainClassForIfOnClassTests {
  public static void main(String[] args) {
  }
}

// Precondition -> DependentUser -> Dependent
// That is, this class and members will be kept only if Precondition and DependentUser are kept.
class Dependent {
  private int intField;
  private String strField;

  Dependent(int i, String s) {
    intField = i;
    strField = s;
  }

  void setI(int i) {
    this.intField = i;
  }

  void setStr(String str) {
    this.strField = str;
  }

  String foo() {
    return strField + intField;
  }
}

class DependentUser {
  int canBeShrinked;

  DependentUser(int i) {
    canBeShrinked = i;
  }

  // The presence of this method will determine that of class Dependent.
  static void callFoo() {
    Dependent d = new Dependent(0, "");
    d.setI(18);
    int x = Integer.valueOf(d.foo());
    d.setStr("0x");
    int y = Integer.valueOf(d.foo());
    System.out.println(y + " - " + x + " == " + (y - x));
  }
}

class Precondition {
  private final int bogus;

  Precondition(int i) {
    this.bogus = i;
  }

  @Override
  public int hashCode() {
    return bogus;
  }
}

