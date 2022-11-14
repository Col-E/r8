// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.testsource;

import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import java.lang.reflect.Field;

@KeepEdge(
    consequences = {
      // Keep the reflectively accessed field.
      @KeepTarget(classConstant = KeepFieldSource.A.class, fieldName = "f")
    })
public class KeepFieldSource {

  public static class A {

    public int f;

    public A(int x) {
      f = x;
    }
  }

  public static void main(String[] args) throws Exception {
    int x = 42 + args.length;
    Object o = System.nanoTime() > 0 ? new A(x) : null;
    Field f = o.getClass().getDeclaredField("f");
    int y = f.getInt(o);
    if (x == y) {
      System.out.println("The values match!");
    }
  }
}
