// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b131810441.sub;

import com.android.tools.r8.NeverInline;

public class Outer {
  private Runnable runner;

  private Outer(int x) {
    runner = new Runnable() {
      @Override
      public void run() {
        System.out.println("Outer#<init>(" + x + ")");
      }
    };
  }

  public static Outer create(int x) {
    return new Outer(x);
  }

  @NeverInline
  public void trigger() {
    runner.run();
  }
}
