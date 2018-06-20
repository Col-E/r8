// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.code;

public class C {
  public static class L {
    public final int x;

    public L(int x) {
      this.x = x;
    }

    public int getX() {
      return x;
    }
  }

  public final static class F {
    public final static F I = new F();

    public int getX() {
      return 123;
    }
  }

  public synchronized static int method1() {
    return new L(1).x;
  }

  public synchronized static int method2() {
    return new L(1).getX();
  }

  public synchronized static int method3() {
    return F.I.getX();
  }
}
