// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation.privatestatic;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;

public class A {
  public String foo() {
    return "A::foo()" + baz() + bar() + bar(0);
  }

  @NeverInline
  @NeverPropagateValue
  private static String baz() {
    return "A::baz()";
  }

  public static String pBaz() {
    return baz();
  }

  @NeverInline
  @NeverPropagateValue
  private static String bar() {
    return "A::bar()";
  }

  public static String pBar() {
    return bar();
  }

  @NeverInline
  @NeverPropagateValue
  private static String bar(int i) {
    return "A::bar(int)";
  }

  public static String pBar1() {
    return bar(1);
  }

  @NeverInline
  @NeverPropagateValue
  private static String blah(int i) {
    return "A::blah(int)";
  }

  public static String pBlah1() {
    return blah(1);
  }

  public void dump() {
    System.out.println(foo());
  }
}

