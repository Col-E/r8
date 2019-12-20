// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.testclasses;

public class TestClassForInnerClass {
  public static class InnerClass {
    public static void m() {
      assert false;
    }
  }

  public static void m() {
    assert false;
  }

  public static void main(String[] args) {
    try {
      m();
    } catch (AssertionError e) {
      System.out.println("AssertionError in TestClassForInnerClass");
    }
    try {
      InnerClass.m();
    } catch (AssertionError e) {
      System.out.println("AssertionError in TestClassForInnerClass.InnerClass");
    }
    System.out.println("DONE");
  }
}
