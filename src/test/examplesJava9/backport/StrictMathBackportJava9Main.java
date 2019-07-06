// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

public class StrictMathBackportJava9Main {

  public static void main(String[] args) {
    testMultiplyExactLongInt();
    testFloorDivLongInt();
    testFloorModLongInt();
  }

  public static void testMultiplyExactLongInt() {
    assertEquals(8L, StrictMath.multiplyExact(2L, 4));
    assertEquals(Long.MAX_VALUE, StrictMath.multiplyExact(Long.MAX_VALUE, 1));
    assertEquals(Long.MIN_VALUE, StrictMath.multiplyExact(Long.MIN_VALUE / 2L, 2));
    try {
      throw new AssertionError(StrictMath.multiplyExact(Long.MAX_VALUE, 2));
    } catch (ArithmeticException expected) {
    }
    try {
      throw new AssertionError(StrictMath.multiplyExact(Long.MIN_VALUE, 2));
    } catch (ArithmeticException expected) {
    }
  }

  public static void testFloorDivLongInt() {
    assertEquals(1L, StrictMath.floorDiv(4L, 4));
    assertEquals(1L, StrictMath.floorDiv(-4L, -4));
    assertEquals(-1L, StrictMath.floorDiv(-4L, 4));
    assertEquals(-1L, StrictMath.floorDiv(4L, -4));

    assertEquals(1L, StrictMath.floorDiv(4L, 3));
    assertEquals(1L, StrictMath.floorDiv(-4L, -3));
    assertEquals(-2L, StrictMath.floorDiv(-4L, 3));
    assertEquals(-2L, StrictMath.floorDiv(4L, -3));

    // Spec edge case: result is actually MAX_VALUE+1 which becomes MIN_VALUE.
    assertEquals(Long.MIN_VALUE, StrictMath.floorDiv(Long.MIN_VALUE, -1));
  }

  public static void testFloorModLongInt() {
    assertEquals(0, StrictMath.floorMod(4L, 4));
    assertEquals(0, StrictMath.floorMod(-4L, -4));
    assertEquals(0, StrictMath.floorMod(-4L, 4));
    assertEquals(0, StrictMath.floorMod(4L, -4));

    assertEquals(1, StrictMath.floorMod(4L, 3));
    assertEquals(-1, StrictMath.floorMod(-4L, -3));
    assertEquals(2, StrictMath.floorMod(-4L, 3));
    assertEquals(-2, StrictMath.floorMod(4L, -3));
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
