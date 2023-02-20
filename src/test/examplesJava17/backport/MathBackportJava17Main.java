// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

public class MathBackportJava17Main {

  public static void main(String[] args) {
    // The methods are actually from Java 15, but we can test them from Java 17.
    testAbsExactInteger();
    testAbsExactLong();
  }

  private static void testAbsExactInteger() {
    assertEquals(42, Math.absExact(42));
    assertEquals(42, Math.absExact(-42));
    assertEquals(Integer.MAX_VALUE, Math.absExact(Integer.MAX_VALUE));
    try {
      throw new AssertionError(Math.absExact(Integer.MIN_VALUE));
    } catch (ArithmeticException expected) {

    }
  }

  private static void testAbsExactLong() {
    assertEquals(42L, Math.absExact(42L));
    assertEquals(42L, Math.absExact(-42L));
    assertEquals(Long.MAX_VALUE, Math.absExact(Long.MAX_VALUE));
    try {
      throw new AssertionError(Math.absExact(Long.MIN_VALUE));
    } catch (ArithmeticException expected) {

    }
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }

  private static void assertEquals(long expected, long actual) {
    if (expected != actual) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
