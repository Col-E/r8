// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
public class Sub1 extends Base implements Itf1 {

  @Override
  public String foo1() {
    return "Sub1::foo1()";
  }

  @NeverInline
  private String bar1(int i) {
    return "Sub1::bar1(" + i + ")";
  }

  public String pBar1() {
    return bar1(1);
  }

  @NeverInline
  private String foo3() {
    return "Sub1::foo3()";
  }

  @Override
  public void dump() {
    System.out.println(foo1());
    System.out.println(foo1(0));
    System.out.println(bar1(0));
    System.out.println(foo3());
  }

}
