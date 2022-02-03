// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.assertionhandler;

import com.android.tools.r8.Keep;

public class AssertionsWithExceptionHandlers {
  @Keep
  public static void assertionsWithCatch1() {
    try {
      assert false : "First assertion";
    } catch (NoSuchMethodError | NoSuchFieldError e) {

    } catch (NoClassDefFoundError e) {

    }
  }

  @Keep
  public static void assertionsWithCatch2() {
    try {
      assert false : "Second assertion";
    } catch (AssertionError e) {
      System.out.println("Caught: " + e.getMessage());
      try {
        assert false : "Third assertion";
      } catch (AssertionError e2) {
        System.out.println("Caught: " + e2.getMessage());
      }
    }
  }

  @Keep
  private static void simpleAssertion() {
    assert false : "Fifth assertion";
  }

  @Keep
  public static void assertionsWithCatch3() {
    try {
      assert false : "Fourth assertion";
    } catch (AssertionError e1) {
      System.out.println("Caught from: " + AssertionHandlers.methodWithAssertionError(e1));
      try {
        simpleAssertion();
      } catch (AssertionError e2) {
        System.out.println("Caught from: " + AssertionHandlers.methodWithAssertionError(e2));
      }
    }
  }

  public static void main(String[] args) {
    try {
      assertionsWithCatch1();
    } catch (AssertionError e) {
      // Ignore.
    }
    assertionsWithCatch2();
    assertionsWithCatch3();
  }
}
