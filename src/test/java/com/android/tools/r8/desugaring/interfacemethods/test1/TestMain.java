// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.test1;

public class TestMain implements InterfaceWithDefaults {
  @Override
  public void test() {
    System.out.println("TestMain::test()");
    this.foo();
    InterfaceWithDefaults.bar(this);
  }

  @Override
  public void foo() {
    System.out.println("TestMain::foo()");
  }

  public static void main(String[] args) {
    new TestMain().test();
  }
}
