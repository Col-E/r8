// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;

@NeverClassInline
public class Base {

  @NeverPropagateValue
  @NeverInline
  private String foo() {
    return "Base::foo()";
  }

  public String pFoo() {
    return foo();
  }

  @NeverPropagateValue
  @NeverInline
  private String foo1() {
    return "Base::foo1()";
  }

  public String pFoo1() {
    return foo1();
  }

  @NeverPropagateValue
  @NeverInline
  private String foo2() {
    return "Base::foo2()";
  }

  public String pFoo2() {
    return foo2();
  }

  @NeverPropagateValue
  @NeverInline
  private String foo3() {
    return "Base::foo3()";
  }

  @NeverInline
  public void dump() {
    System.out.println(foo());
    System.out.println(foo1());
    System.out.println(foo2());
    System.out.println(foo3());
  }
}
