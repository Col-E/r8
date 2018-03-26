// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.test2;

public class TestMain implements Test2 {
  public static void main(String... args) {
    TestMain m = new TestMain();
    System.out.println(m.bar("first"));
    System.out.println(m.foo("second"));
    System.out.println(m.fooDelegate("third"));
  }

  private String fooDelegate(String a) {
    return "TestMain::fooDelegate(" + Test2.super.foo(a) + ")";
  }
}

