// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.exceptionhandling;

public class ExceptionHandlingTestClass {

  private static boolean FALSE;

  // -keep
  public static void main(String[] args) {
    FALSE = args == null;
    try {
      methodWithoutCatchHandlersTest(1);
      System.out.println("Test succeeded: methodWithoutCatchHandlersTest(1)");
    } catch (Exception e) {
      System.out.println("Test failed: methodWithoutCatchHandlersTest(1)");
    }
    try {
      methodWithoutCatchHandlersTest(2);
      System.out.println("Test failed: methodWithoutCatchHandlersTest(2)");
    } catch (Exception e) {
      System.out.println("Test succeeded: methodWithoutCatchHandlersTest(2)");
    }
    try {
      methodWithoutCatchHandlersTest(3);
      System.out.println("Test failed: methodWithoutCatchHandlersTest(3)");
    } catch (Exception e) {
      System.out.println("Test succeeded: methodWithoutCatchHandlersTest(3)");
    }

    methodWithCatchHandlersTest();
  }

  // -neverinline
  private static void methodWithoutCatchHandlersTest(int i) {
    switch (i) {
      case 1:
        inlineeWithNormalExitThatDoesNotThrow();
        break;

      case 2:
        inlineeWithNormalExitThatThrows();
        break;

      case 3:
        inlineeWithoutNormalExit();
        break;
    }
  }

  // -neverinline
  private static void methodWithCatchHandlersTest() {
    try {
      inlineeWithNormalExitThatDoesNotThrow();
      System.out.println("Test succeeded: methodWithCatchHandlersTest(1)");
    } catch (Exception e) {

    }
    try {
      inlineeWithNormalExitThatThrows();
    } catch (Exception e) {
      System.out.println("Test succeeded: methodWithCatchHandlersTest(2)");
    }
    try {
      inlineeWithoutNormalExit();
    } catch (Exception e) {
      System.out.println("Test succeeded: methodWithCatchHandlersTest(3)");
    }
  }

  // -forceinline
  private static void inlineeWithNormalExitThatDoesNotThrow() {
    if (FALSE) {
      throw new RuntimeException();
    }
  }

  // -forceinline
  private static void inlineeWithNormalExitThatThrows() {
    if (!FALSE) {
      throw new RuntimeException();
    }
  }

  // -forceinline
  private static void inlineeWithoutNormalExit() {
    throw new RuntimeException();
  }
}
