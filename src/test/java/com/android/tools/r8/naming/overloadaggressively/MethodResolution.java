// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import java.lang.reflect.Method;

public class MethodResolution {
  public static void main(String[] args) throws Exception {
    B b = new B();

    int originalF1 = b.getF1();
    Method getF1 = B.class.getMethod("getF1", (Class[]) null);
    int diff = ((Integer) getF1.invoke(b)) - originalF1;
    System.out.println("diff: " + diff);

    Object originalF2 = b.getF2();
    Method getF2 = B.class.getMethod("getF2", (Class[]) null);
    System.out.println(originalF2 + " v.s. " + getF2.invoke(b));

    String originalF3 = b.getF3();
    Method getF3 = B.class.getMethod("getF3", (Class[]) null);
    System.out.println(originalF3 + " v.s. " + getF3.invoke(b));
  }
}
