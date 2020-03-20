// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation.packageprivate.package_b;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.accessrelaxation.packageprivate.package_a.A;

@NeverClassInline
public class B extends A {

  @NeverInline
  public void foo() {
    System.out.println("B.foo");
  }
}
