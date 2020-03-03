// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.proxy.testclasses;

public class TestClass extends BaseClass implements SubInterface, Interface2 {
  public final String name;  // Must be public to allow inlining.

  TestClass(String name) {
    this.name = name;
  }

  @Override
  public void method() {
    System.out.println(name);
  }
}
