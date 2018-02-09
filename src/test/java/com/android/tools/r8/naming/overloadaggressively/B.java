// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

public class B {
  volatile int f1 = 8;
  volatile Object f2 = "d8";
  volatile String f3 = "r8";

  public int getF1() {
    return f1;
  }

  public Object getF2() {
    return f2;
  }

  public String getF3() {
    return f3;
  }
}
