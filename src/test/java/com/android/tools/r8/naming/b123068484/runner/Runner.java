// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b123068484.runner;

import com.android.tools.r8.naming.b123068484.data.PublicAbs;
import com.android.tools.r8.naming.b123068484.data.Concrete1;
import com.android.tools.r8.naming.b123068484.data.Concrete2;

public class Runner {
  private static int counter = 0;
  static PublicAbs create(String x) {
    if (counter++ % 2 == 0) {
      return new Concrete1(x);
    } else {
      return new Concrete2(x);
    }
  }

  public static void main(String[] args) {
    PublicAbs instance = create("Runner");
    if (instance instanceof Concrete1) {
      System.out.println(((Concrete1) instance).strField);
    } else {
      System.out.println(((Concrete2) instance).strField);
    }
  }
}
