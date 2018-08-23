// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import java.lang.reflect.Method;

public class MethodResolution {
  private static Method getMethodPreferInt(String name) throws NoSuchMethodException {
    if (name == null) {
      throw new NullPointerException();
    }
    Method matchingMethod = null;
    for (Method m : B.class.getMethods()) {
      if (!m.getName().equals(name)) {
        continue;
      }
      if (m.getReturnType() == int.class) {
        return m;
      }
      if (matchingMethod == null) {
        matchingMethod = m;
      }
    }
    if (matchingMethod == null) {
      throw new NoSuchMethodException();
    }
    return matchingMethod;
  }

  public static void main(String[] args) throws Exception {
    B b = new B();

    int originalF1 = b.getF1();
    // String constant of the method name must be provided directly to getMethod() otherwise it
    // won't be obfuscated.
    Method getF1 = getMethodPreferInt(B.class.getMethod("getF1", (Class[]) null).getName());
    int diff = ((Integer) getF1.invoke(b)) - originalF1;
    System.out.println("diff: " + diff);

    Object originalF2 = b.getF2();
    Method getF2 = getMethodPreferInt(B.class.getMethod("getF2", (Class[]) null).getName());
    System.out.println(originalF2 + " v.s. " + getF2.invoke(b));

    String originalF3 = b.getF3();
    Method getF3 = getMethodPreferInt(B.class.getMethod("getF3", (Class[]) null).getName());
    System.out.println(originalF3 + " v.s. " + getF3.invoke(b));
  }
}
