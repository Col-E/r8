// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.neverreturnsnormally;

import com.android.tools.r8.ForceInline;

public class TestClass {
  public static boolean throwNpe(String message) {
    String newMessage = "prefix:" + message + ":suffix";
    System.out.println("Before throwing NPE: " + newMessage);
    throw new NullPointerException(newMessage);
  }

  @ForceInline
  public static int throwToBeInlined() {
    throwNpe("throwToBeInlined");
    return "Nobody cares".length();
  }

  public static boolean throwOrLoop(boolean b) {
    System.out.println("Inside TestClass::throwOrLoop");
    if (b) {
      while (true) {
      }
    } else {
      throwNpe("");
      return false; // Effectively unreachable
    }
  }

  private int alwaysThrow(boolean b) {
    int millis = (int) System.currentTimeMillis();
    if (((millis & 1) == 0) == b) {
      throw new AssertionError("alwaysThrow::if-then");
    } else {
      switch (millis) {
        case 1:
          throw new AssertionError("alwaysThrow::case 1");
        case 2:
          throwNpe("alwaysThrow::case 2");
          return alwaysThrow(!b);
        default:
          throwNpe("alwaysThrow::default");
      }
    }
    return millis;
  }

  private boolean loop(boolean b) {
    while (true) {
      if (b) {
        throwNpe("");
        return b;
      }
    }
  }

  private static int innerNotReachable() {
    while (true) {
      if ((System.currentTimeMillis() & 1) == 0) {
        throwNpe("");
        return 123;
      } else {
        innerNotReachable();
      }
    }
  }

  private static int outerTrivial() {
    return innerNotReachable();
  }

  static void assertRemoved(String message) {
    System.out.println("The method call is unreachable: " + message);
    assertRemoved("recursive call");
  }

  public static void testTrivial() {
    throwOrLoop(false);
    assertRemoved("testTrivial");
  }

  public static void testInOneBranch(boolean b) {
    if (b) {
      throwOrLoop(true);
      assertRemoved("testInOneBranch");
    } else {
      System.out.println("Non-throwing path");
    }
  }

  public static void testInTwoBranches(int i) {
    while (i > 0) {
      TestClass testClass = new TestClass();
      if ((i % 2) == 0) {
        testClass.alwaysThrow(true);
      } else {
        testClass.loop(true);
      }
      i--;
      assertRemoved("testInTwoBranches");
    }
  }

  public static int testWithValueBeingUsed(int i) {
    TestClass testClass = new TestClass();
    int interim = testClass.alwaysThrow(false);
    interim += testClass.alwaysThrow(true);
    assertRemoved("testWithValueBeingUsed");
    return interim * i;
  }

  public static int testWithTryCatch(int i) {
    TestClass testClass = new TestClass();
    int result = i;
    try {
      result += testClass.alwaysThrow(false);
      assertRemoved("testWithTryCatch::try");
    } catch (Exception e) {
      result += testClass.loop(true) ? +1 : -1;
      assertRemoved("testWithTryCatch::catch");
    }
    assertRemoved("testWithTryCatch::main");
    return result;
  }

  public static int testWithTryFinally(int i) {
    TestClass testClass = new TestClass();
    int result = i;
    try {
      result += testClass.alwaysThrow(false);
      assertRemoved("testWithTryFinally::try");
    } finally {
      result += testClass.loop(true) ? +1 : -1;
      assertRemoved("testWithTryFinally::finally");
    }
    assertRemoved("testWithTryFinally::main");
    return result;
  }

  public static void testInlinedIntoVoidMethod() {
    throwToBeInlined();
    assertRemoved("testInlinedIntoVoidMethod");
  }

  public static void testOuterTrivial() {
    outerTrivial();
    assertRemoved("testOuterTrivial");
  }

  public static void main(String[] args) {
    try {
      switch (args.length) {
        case 0:
          testTrivial();
          break;
        case 1:
          testInOneBranch(true);
          break;
        case 2:
          testInOneBranch(false);
          break;
        case 3:
          testInTwoBranches(0);
          break;
        case 4:
          testInTwoBranches(1);
          break;
        case 5:
          testInTwoBranches(10);
          break;
        case 6:
          testWithValueBeingUsed(6);
          break;
        case 7:
          testWithTryCatch(7);
          break;
        case 8:
          testWithTryFinally(8);
          break;
        case 9:
          testInlinedIntoVoidMethod();
          break;
        case 10:
          testOuterTrivial();
          break;
      }
    } catch (Exception e) {
      System.out.println("e = " + e);
    }
  }
}
