// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
public class Sub2 extends Base implements Itf2 {

  @Override
  public String foo2() {
    return "Sub2::foo2()";
  }

  @NeverInline
  private String bar1(int i) {
    return "Sub2::bar1(" + i + ")";
  }

  public String pBar1() {
    return bar1(1);
  }

  @NeverInline
  private String bar2(int i) {
    return "Sub2::bar2(" + i + ")";
  }

  public String pBar2() {
    return bar2(2);
  }

  @NeverInline
  private String foo3() {
    return "Sub2::foo3()";
  }

  @Override
  public void dump() {
    System.out.println(foo2());
    System.out.println(foo2(0));
    System.out.println(bar2(0));
    System.out.println(foo3());
    try {
      bar1(0);
    } catch (AssertionError e) {
      // expected
    }
  }

}
