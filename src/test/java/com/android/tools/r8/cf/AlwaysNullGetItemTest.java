// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

public class AlwaysNullGetItemTest {
  public static void main(String[] args) {
    try {
      System.out.println(foo());
      System.out.println(bar());
      System.out.println(hello().hello());
      System.out.println(goodbye().hello());
      throw new RuntimeException("Expected NullPointerException");
    } catch (NullPointerException e) {
      System.out.println("NullPointerException");
    }
  }

  private static Object foo() {
    return ((Object[]) null)[0];
  }

  private static Object bar() {
    return getObjectArray()[0];
  }

  private static A hello() {
    return getTypedArray()[0].hello();
  }

  private static A goodbye() {
    return getTypedArray()[0].goodbye();
  }

  private static Object[] getObjectArray() {
    return null;
  }

  private static A[] getTypedArray() {
    return null;
  }

  private static class A {
    A hello() {
      return this;
    }

    A goodbye() {
      return null;
    }
  }
}
