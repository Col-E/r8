// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation.privatestatic;

public class A {
  public String foo() {
    return "A::foo()" + baz() + bar() + bar(0);
  }

  // NOTE: here and below 'synchronized' is supposed to disable inlining of this method.
  private synchronized static String baz() {
    return "A::baz()";
  }

  public static String pBaz() {
    return baz();
  }

  private synchronized static String bar() {
    return "A::bar()";
  }

  public static String pBar() {
    return bar();
  }

  private synchronized static String bar(int i) {
    return "A::bar(int)";
  }

  public static String pBar1() {
    return bar(1);
  }

  private synchronized static String blah(int i) {
    return "A::blah(int)";
  }

  public static String pBlah1() {
    return blah(1);
  }

  public void dump() {
    System.out.println(foo());
  }
}

