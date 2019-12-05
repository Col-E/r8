// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.code;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverInline;

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

  @AssumeMayHaveSideEffects
  @NeverInline
  public static int method1() {
    return new L(1).x;
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  public static int method2() {
    return new L(1).getX();
  }

  @AssumeMayHaveSideEffects
  @NeverInline
  public static int method3() {
    return F.I.getX();
  }
}
