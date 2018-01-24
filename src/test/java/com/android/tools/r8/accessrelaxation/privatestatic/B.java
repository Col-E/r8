// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation.privatestatic;

public class B extends A implements I {
  public String bar() {
    try {
      return "B::bar()" + bar(0);
    } catch (Throwable e) {
      return "B::bar() >> " + e.getClass().getName();
    }
  }

  private synchronized static String blah(int i) {
    return "B::blah(int)";
  }

  public static String pBlah1() {
    return blah(1);
  }

  public void dump() {
    System.out.println(this.bar());
    try {
      System.out.println(this.bar(0));
    } catch (Throwable e) {
      System.out.println(e.getClass().getName());
    }
    System.out.println(this.foo());
    System.out.println(blah(0));
  }
}

