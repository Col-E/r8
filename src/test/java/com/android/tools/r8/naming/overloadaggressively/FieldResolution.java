// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import java.lang.reflect.Field;
import java.util.Random;

public class FieldResolution {
  public static void main(String[] args) throws Exception {
    A a = new A();
    B b = new B();

    Field f3 = A.class.getField("f3");
    f3.set(a, b);
    assert a.f3 != null;
    assert a.f3 == b;

    Field f1 = A.class.getField("f1");
    Random random = new Random();
    int next = random.nextInt();
    f1.set(a, next);
    a.f3.f1 = next;
    int diff = a.f1 - b.f1;
    System.out.println("diff: " + diff);
  }
}
