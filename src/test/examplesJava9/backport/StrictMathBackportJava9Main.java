// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.math.BigInteger;

public class StrictMathBackportJava9Main {

  public static void main(String[] args) {
    testMultiplyExactLongInt();
    testMultiplyFull();
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

  public static void testMultiplyFull() {
    assertEquals(8L, StrictMath.multiplyFull(2, 4));
    assertEquals(4611686014132420609L,
        StrictMath.multiplyFull(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertEquals(-4611686016279904256L,
        StrictMath.multiplyFull(Integer.MAX_VALUE, Integer.MIN_VALUE));
    assertEquals(4611686018427387904L,
        StrictMath.multiplyFull(Integer.MIN_VALUE, Integer.MIN_VALUE));
  }

  public static void testMultiplyHigh() {
    long[] interestingValues = {
        Long.MIN_VALUE, Long.MAX_VALUE,
        Integer.MIN_VALUE, Integer.MAX_VALUE,
        Short.MIN_VALUE, Short.MAX_VALUE,
        Byte.MIN_VALUE, Byte.MAX_VALUE,
        0L,
        -1L, 1L,
        -42L, 42L
    };
    for (long x : interestingValues) {
      for (long y : interestingValues) {
        long expected = BigInteger.valueOf(x)
            .multiply(BigInteger.valueOf(y))
            .shiftRight(64)
            .longValue();
        assertEquals(expected, StrictMath.multiplyHigh(x, y));
      }
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
