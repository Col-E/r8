// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class StrictMathBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public StrictMathBackportTest(TestParameters parameters) {
    super(parameters, StrictMath.class, Main.class);
    registerTarget(AndroidApiLevel.N, 75);
    ignoreInvokes("nextAfter");  // Available in API 9, used to test nextDown.
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testAddExactInteger();
      testAddExactLong();
      testFloorDivInteger();
      testFloorDivLong();
      testFloorModInteger();
      testFloorModLong();
      testMultiplyExactInteger();
      testMultiplyExactLong();
      testNextDownDouble();
      testNextDownFloat();
      testSubtractExactInteger();
      testSubtractExactLong();
      testToIntExact();
    }

    private static void testAddExactInteger() {
      assertEquals(2, StrictMath.addExact(1, 1));
      assertEquals(-2, StrictMath.addExact(-1, -1));

      assertEquals(Integer.MAX_VALUE, StrictMath.addExact(Integer.MAX_VALUE, 0));
      assertEquals(Integer.MIN_VALUE, StrictMath.addExact(Integer.MIN_VALUE, 0));

      try {
        throw new AssertionError(StrictMath.addExact(1, Integer.MAX_VALUE));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.addExact(-1, Integer.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testAddExactLong() {
      assertEquals(2L, StrictMath.addExact(1L, 1L));
      assertEquals(-2L, StrictMath.addExact(-1L, -1L));

      assertEquals(Long.MAX_VALUE, StrictMath.addExact(Long.MAX_VALUE, 0L));
      assertEquals(Long.MIN_VALUE, StrictMath.addExact(Long.MIN_VALUE, 0L));

      try {
        throw new AssertionError(StrictMath.addExact(1L, Long.MAX_VALUE));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.addExact(-1L, Long.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testFloorDivInteger() {
      assertEquals(1, StrictMath.floorDiv(4, 3));
      assertEquals(1, StrictMath.floorDiv(-4, -3));
      assertEquals(-2, StrictMath.floorDiv(-4, 3));
      assertEquals(-2, StrictMath.floorDiv(4, -3));

      assertEquals(1, StrictMath.floorDiv(4, 3));
      assertEquals(1, StrictMath.floorDiv(-4, -3));
      assertEquals(-2, StrictMath.floorDiv(-4, 3));
      assertEquals(-2, StrictMath.floorDiv(4, -3));

      // Spec edge case: result is actually MAX_VALUE+1 which becomes MIN_VALUE.
      assertEquals(Integer.MIN_VALUE, StrictMath.floorDiv(Integer.MIN_VALUE, -1));
    }

    private static void testFloorDivLong() {
      assertEquals(1L, StrictMath.floorDiv(4L, 4L));
      assertEquals(1L, StrictMath.floorDiv(-4L, -4L));
      assertEquals(-1L, StrictMath.floorDiv(-4L, 4L));
      assertEquals(-1L, StrictMath.floorDiv(4L, -4L));

      assertEquals(1L, StrictMath.floorDiv(4L, 3L));
      assertEquals(1L, StrictMath.floorDiv(-4L, -3L));
      assertEquals(-2L, StrictMath.floorDiv(-4L, 3L));
      assertEquals(-2L, StrictMath.floorDiv(4L, -3L));

      // Spec edge case: result is actually MAX_VALUE+1 which becomes MIN_VALUE.
      assertEquals(Long.MIN_VALUE, StrictMath.floorDiv(Long.MIN_VALUE, -1L));
    }

    private static void testFloorModInteger() {
      assertEquals(0, StrictMath.floorMod(4, 4));
      assertEquals(0, StrictMath.floorMod(-4, -4));
      assertEquals(0, StrictMath.floorMod(-4, 4));
      assertEquals(0, StrictMath.floorMod(4, -4));

      assertEquals(1L, StrictMath.floorMod(4L, 3L));
      assertEquals(-1L, StrictMath.floorMod(-4L, -3L));
      assertEquals(2L, StrictMath.floorMod(-4L, 3L));
      assertEquals(-2L, StrictMath.floorMod(4L, -3L));
    }

    private static void testFloorModLong() {
      assertEquals(0L, StrictMath.floorMod(4L, 4L));
      assertEquals(0L, StrictMath.floorMod(-4L, -4L));
      assertEquals(0L, StrictMath.floorMod(-4L, 4L));
      assertEquals(0L, StrictMath.floorMod(4L, -4L));

      assertEquals(1L, StrictMath.floorMod(4L, 3L));
      assertEquals(-1L, StrictMath.floorMod(-4L, -3L));
      assertEquals(2L, StrictMath.floorMod(-4L, 3L));
      assertEquals(-2L, StrictMath.floorMod(4L, -3L));
    }

    private static void testMultiplyExactInteger() {
      assertEquals(8, StrictMath.multiplyExact(2, 4));
      assertEquals(Integer.MAX_VALUE, StrictMath.multiplyExact(Integer.MAX_VALUE, 1));
      assertEquals(Integer.MIN_VALUE, StrictMath.multiplyExact(Integer.MIN_VALUE / 2, 2));

      try {
        throw new AssertionError(StrictMath.multiplyExact(Integer.MAX_VALUE, 2));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.multiplyExact(Integer.MIN_VALUE, 2));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testMultiplyExactLong() {
      assertEquals(8L, StrictMath.multiplyExact(2L, 4L));
      assertEquals(Long.MAX_VALUE, StrictMath.multiplyExact(Long.MAX_VALUE, 1L));
      assertEquals(Long.MIN_VALUE, StrictMath.multiplyExact(Long.MIN_VALUE / 2L, 2L));

      try {
        throw new AssertionError(StrictMath.multiplyExact(Long.MAX_VALUE, 2L));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.multiplyExact(Long.MIN_VALUE, 2L));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testNextDownDouble() {
      double[] interestingValues = {
          Double.MIN_VALUE, Double.MAX_VALUE,
          Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
          Integer.MIN_VALUE, Integer.MAX_VALUE,
          Long.MIN_VALUE, Long.MAX_VALUE,
          Double.NaN,
          0d,
          -1d, 1d,
          -0.5d, 0.5d,
          -0.1d, 0.1d,
      };
      for (double interestingValue : interestingValues) {
        assertEquals(StrictMath.nextAfter(interestingValue, Double.NEGATIVE_INFINITY),
            StrictMath.nextDown(interestingValue));
      }
    }

    private static void testNextDownFloat() {
      float[] interestingValues = {
          Float.MIN_VALUE, Float.MAX_VALUE,
          Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
          Integer.MIN_VALUE, Integer.MAX_VALUE,
          Long.MIN_VALUE, Long.MAX_VALUE,
          Float.NaN,
          0f,
          -1f, 1f,
          -0.5f, 0.5f,
          -0.1f, 0.1f,
      };
      for (float interestingValue : interestingValues) {
        assertEquals(StrictMath.nextAfter(interestingValue, Float.NEGATIVE_INFINITY),
            StrictMath.nextDown(interestingValue));
      }
    }

    private static void testSubtractExactInteger() {
      assertEquals(-1, StrictMath.subtractExact(0, 1));
      assertEquals(1, StrictMath.subtractExact(0, -1));

      assertEquals(Integer.MAX_VALUE, StrictMath.subtractExact(Integer.MAX_VALUE, 0));
      assertEquals(Integer.MIN_VALUE, StrictMath.subtractExact(Integer.MIN_VALUE, 0));

      try {
        throw new AssertionError(StrictMath.subtractExact(Integer.MIN_VALUE, 1));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.subtractExact(Integer.MAX_VALUE, -1));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testSubtractExactLong() {
      assertEquals(-1L, StrictMath.subtractExact(0L, 1L));
      assertEquals(1L, StrictMath.subtractExact(0L, -1L));

      assertEquals(Long.MAX_VALUE, StrictMath.subtractExact(Long.MAX_VALUE, 0L));
      assertEquals(Long.MIN_VALUE, StrictMath.subtractExact(Long.MIN_VALUE, 0L));

      try {
        throw new AssertionError(StrictMath.subtractExact(Long.MIN_VALUE, 1L));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.subtractExact(Long.MAX_VALUE, -1L));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testToIntExact() {
      assertEquals(0, StrictMath.toIntExact(0L));
      assertEquals(Integer.MAX_VALUE, StrictMath.toIntExact(Integer.MAX_VALUE));
      assertEquals(Integer.MIN_VALUE, StrictMath.toIntExact(Integer.MIN_VALUE));

      try {
        throw new AssertionError(StrictMath.toIntExact(Integer.MAX_VALUE + 1L));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(StrictMath.toIntExact(Integer.MIN_VALUE - 1L));
      } catch (ArithmeticException expected) {
      }
    }
  }
}
