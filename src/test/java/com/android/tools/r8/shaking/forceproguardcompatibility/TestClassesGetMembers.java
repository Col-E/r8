// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class TestClassWithMembers {
  int unused;
  static String foo = "foo";
  String bar(String s) {
    return foo + s;
  }
}

class TestMainWithGetMembers {
  public static void main(String[] args) throws Exception {
    Field foo = TestClassWithMembers.class.getDeclaredField("foo");
    System.out.println(foo.get(null));

    TestClassWithMembers instance = new TestClassWithMembers();
    Method bar =
        TestClassWithMembers.class.getDeclaredMethod("bar", new Class[] { String.class });
    String barResult = (String) bar.invoke(instance, "bar");
    assert barResult.startsWith("foo");
    assert barResult.endsWith("bar");
    System.out.println(barResult);
  }
}
