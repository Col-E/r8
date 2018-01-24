// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation.privatestatic;

public class BB extends A {
  private synchronized static String blah(int i) {
    return "BB::blah(int)";
  }

  public static String pBlah1() {
    return blah(1);
  }

  public void dump() {
    System.out.println(this.foo());
  }
}

