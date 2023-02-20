// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

public class StrictMathBackportJava17Main {

  public static void main(String[] args) {
    // The methods are actually from Java 15, but we can test them from Java 17.
    testAbsExactInteger();
    testAbsExactLong();
    // The methods are actually from Java 14, but we can test them from Java 17.
    testDecrementExactInteger();
    testDecrementExactLong();
    testIncrementExactInteger();
    testIncrementExactLong();
    testNegateExactInteger();
    testNegateExactLong();
  }

  private static void testAbsExactInteger() {
    assertEquals(42, StrictMath.absExact(42));
    assertEquals(42, StrictMath.absExact(-42));
    assertEquals(Integer.MAX_VALUE, StrictMath.absExact(Integer.MAX_VALUE));
    try {
      throw new AssertionError(StrictMath.absExact(Integer.MIN_VALUE));
    } catch (ArithmeticException expected) {

    }
  }

  private static void testAbsExactLong() {
    assertEquals(42L, StrictMath.absExact(42L));
    assertEquals(42L, StrictMath.absExact(-42L));
    assertEquals(Long.MAX_VALUE, StrictMath.absExact(Long.MAX_VALUE));
    try {
      throw new AssertionError(StrictMath.absExact(Long.MIN_VALUE));
    } catch (ArithmeticException expected) {

    }
  }

  private static void testDecrementExactInteger() {
    assertEquals(-1, StrictMath.decrementExact(0));
    assertEquals(Integer.MIN_VALUE, StrictMath.decrementExact(Integer.MIN_VALUE + 1));

    try {
      throw new AssertionError(StrictMath.decrementExact(Integer.MIN_VALUE));
    } catch (ArithmeticException expected) {
    }
  }

  private static void testDecrementExactLong() {
    assertEquals(-1L, StrictMath.decrementExact(0L));
    assertEquals(Long.MIN_VALUE, StrictMath.decrementExact(Long.MIN_VALUE + 1L));

    try {
      throw new AssertionError(StrictMath.decrementExact(Long.MIN_VALUE));
    } catch (ArithmeticException expected) {
    }
  }

  private static void testIncrementExactInteger() {
    assertEquals(1, StrictMath.incrementExact(0));
    assertEquals(Integer.MAX_VALUE, StrictMath.incrementExact(Integer.MAX_VALUE - 1));

    try {
      throw new AssertionError(StrictMath.incrementExact(Integer.MAX_VALUE));
    } catch (ArithmeticException expected) {
    }
  }

  private static void testIncrementExactLong() {
    assertEquals(1L, StrictMath.incrementExact(0L));
    assertEquals(Long.MAX_VALUE, StrictMath.incrementExact(Long.MAX_VALUE - 1L));

    try {
      throw new AssertionError(StrictMath.incrementExact(Long.MAX_VALUE));
    } catch (ArithmeticException expected) {
    }
  }

  private static void testNegateExactInteger() {
    assertEquals(0, StrictMath.negateExact(0));
    assertEquals(-1, StrictMath.negateExact(1));
    assertEquals(1, StrictMath.negateExact(-1));
    assertEquals(-2_147_483_647, StrictMath.negateExact(Integer.MAX_VALUE));

    try {
      throw new AssertionError(StrictMath.negateExact(Integer.MIN_VALUE));
    } catch (ArithmeticException expected) {
    }
  }

  private static void testNegateExactLong() {
    assertEquals(0L, StrictMath.negateExact(0L));
    assertEquals(-1L, StrictMath.negateExact(1L));
    assertEquals(1L, StrictMath.negateExact(-1L));
    assertEquals(-9_223_372_036_854_775_807L, StrictMath.negateExact(Long.MAX_VALUE));

    try {
      throw new AssertionError(StrictMath.negateExact(Long.MIN_VALUE));
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
