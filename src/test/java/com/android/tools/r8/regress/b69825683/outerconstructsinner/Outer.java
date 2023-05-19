// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b69825683.outerconstructsinner;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverInline;

public class Outer {

  @AssumeMayHaveSideEffects
  @NeverInline
  public Outer() {
    new Inner();
  }

  public class Inner {

    @AssumeMayHaveSideEffects
    @NeverInline
    private Inner() {}
  }

  public static void main(String[] args) {
    new Outer();
    for (java.lang.reflect.Constructor<?> m : Outer.Inner.class.getDeclaredConstructors()) {
      System.out.println(m.getName());
    }
  }
}
