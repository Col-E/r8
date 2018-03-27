// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

public class BaseDefaultMethodTest {

  public static final Class[] CLASSES = {
      BaseDefaultMethodTest.class,
      Base.class,
      Derived.class,
      Impl.class,
  };

  interface Base {
    default void bar() {}
  }

  interface Derived extends Base {}

  static class Impl implements Derived {}

  static Derived foo() {
    return new Impl();
  }

  public static void main(String[] args) {
    Derived d = foo();
    d.bar();
  }
}
