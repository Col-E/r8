// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

public class Sub2 extends Base implements Itf2 {

  @Override
  public String foo2() {
    return "Sub2::foo2()";
  }

  private synchronized String bar2(int i) {
    return "Sub2::bar2(" + i + ")";
  }

  public String pBar2() {
    return bar2(2);
  }

  @Override
  public void dump() {
    System.out.println(foo2());
    System.out.println(foo2(0));
    System.out.println(bar2(0));
  }

}
