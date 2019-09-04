// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import java.lang.reflect.Method;

class WarnReflectiveAccessTest {
  int counter = 0;

  WarnReflectiveAccessTest() {}

  public void foo() {
    System.out.println("TestMain::foo(" + counter++ + ")");
  }

  public int boo() {
    System.out.println("TestMain::boo(" + counter + ")");
    return counter;
  }

  public static void main(String[] args) throws Exception {
    WarnReflectiveAccessTest instance = new WarnReflectiveAccessTest();

    StringBuilder builder = new StringBuilder();
    builder.append("f");
    builder.append("o").append("o");
    Method foo = instance.getClass().getDeclaredMethod(builder.toString()); // Marked line.
    foo.invoke(instance);
  }
}
