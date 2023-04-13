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
public final class MathBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public MathBackportTest(TestParameters parameters) {
    super(parameters, Math.class, Main.class);
    registerTarget(AndroidApiLevel.N, 97);
    ignoreInvokes("nextAfter");  // Available in API 9, used to test nextDown.
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testAddExactInteger();
      testAddExactLong();
      testDecrementExactInteger();
      testDecrementExactLong();
      testFloorDivInteger();
      testFloorDivLong();
      testFloorModInteger();
      testFloorModLong();
      testIncrementExactInteger();
      testIncrementExactLong();
      testMultiplyExactInteger();
      testMultiplyExactLong();
      testNegateExactInteger();
      testNegateExactLong();
      testNextDownDouble();
      testNextDownFloat();
      testSubtractExactInteger();
      testSubtractExactLong();
      testToIntExact();
    }

    private static void testAddExactInteger() {
      assertEquals(2, Math.addExact(1, 1));
      assertEquals(-2, Math.addExact(-1, -1));

      assertEquals(Integer.MAX_VALUE, Math.addExact(Integer.MAX_VALUE, 0));
      assertEquals(Integer.MIN_VALUE, Math.addExact(Integer.MIN_VALUE, 0));

      try {
        throw new AssertionError(Math.addExact(1, Integer.MAX_VALUE));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.addExact(-1, Integer.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testAddExactLong() {
      assertEquals(2L, Math.addExact(1L, 1L));
      assertEquals(-2L, Math.addExact(-1L, -1L));

      assertEquals(Long.MAX_VALUE, Math.addExact(Long.MAX_VALUE, 0L));
      assertEquals(Long.MIN_VALUE, Math.addExact(Long.MIN_VALUE, 0L));

      try {
        throw new AssertionError(Math.addExact(1L, Long.MAX_VALUE));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.addExact(-1L, Long.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testDecrementExactInteger() {
      assertEquals(-1, Math.decrementExact(0));
      assertEquals(Integer.MIN_VALUE, Math.decrementExact(Integer.MIN_VALUE + 1));

      try {
        throw new AssertionError(Math.decrementExact(Integer.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testDecrementExactLong() {
      assertEquals(-1L, Math.decrementExact(0L));
      assertEquals(Long.MIN_VALUE, Math.decrementExact(Long.MIN_VALUE + 1L));

      try {
        throw new AssertionError(Math.decrementExact(Long.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testFloorDivInteger() {
      assertEquals(1, Math.floorDiv(4, 4));
      assertEquals(1, Math.floorDiv(-4, -4));
      assertEquals(-1, Math.floorDiv(-4, 4));
      assertEquals(-1, Math.floorDiv(4, -4));

      assertEquals(1, Math.floorDiv(4, 3));
      assertEquals(1, Math.floorDiv(-4, -3));
      assertEquals(-2, Math.floorDiv(-4, 3));
      assertEquals(-2, Math.floorDiv(4, -3));

      // Spec edge case: result is actually MAX_VALUE+1 which becomes MIN_VALUE.
      assertEquals(Integer.MIN_VALUE, Math.floorDiv(Integer.MIN_VALUE, -1));
    }

    private static void testFloorDivLong() {
      assertEquals(1L, Math.floorDiv(4L, 4L));
      assertEquals(1L, Math.floorDiv(-4L, -4L));
      assertEquals(-1L, Math.floorDiv(-4L, 4L));
      assertEquals(-1L, Math.floorDiv(4L, -4L));

      assertEquals(1L, Math.floorDiv(4L, 3L));
      assertEquals(1L, Math.floorDiv(-4L, -3L));
      assertEquals(-2L, Math.floorDiv(-4L, 3L));
      assertEquals(-2L, Math.floorDiv(4L, -3L));

      // Spec edge case: result is actually MAX_VALUE+1 which becomes MIN_VALUE.
      assertEquals(Long.MIN_VALUE, Math.floorDiv(Long.MIN_VALUE, -1L));
    }

    private static void testFloorModInteger() {
      assertEquals(0, Math.floorMod(4, 4));
      assertEquals(0, Math.floorMod(-4, -4));
      assertEquals(0, Math.floorMod(-4, 4));
      assertEquals(0, Math.floorMod(4, -4));

      assertEquals(1L, Math.floorMod(4L, 3L));
      assertEquals(-1L, Math.floorMod(-4L, -3L));
      assertEquals(2L, Math.floorMod(-4L, 3L));
      assertEquals(-2L, Math.floorMod(4L, -3L));
    }

    private static void testFloorModLong() {
      assertEquals(0L, Math.floorMod(4L, 4L));
      assertEquals(0L, Math.floorMod(-4L, -4L));
      assertEquals(0L, Math.floorMod(-4L, 4L));
      assertEquals(0L, Math.floorMod(4L, -4L));

      assertEquals(1L, Math.floorMod(4L, 3L));
      assertEquals(-1L, Math.floorMod(-4L, -3L));
      assertEquals(2L, Math.floorMod(-4L, 3L));
      assertEquals(-2L, Math.floorMod(4L, -3L));
    }

    private static void testIncrementExactInteger() {
      assertEquals(1, Math.incrementExact(0));
      assertEquals(Integer.MAX_VALUE, Math.incrementExact(Integer.MAX_VALUE - 1));

      try {
        throw new AssertionError(Math.incrementExact(Integer.MAX_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testIncrementExactLong() {
      assertEquals(1L, Math.incrementExact(0L));
      assertEquals(Long.MAX_VALUE, Math.incrementExact(Long.MAX_VALUE - 1L));

      try {
        throw new AssertionError(Math.incrementExact(Long.MAX_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testMultiplyExactInteger() {
      assertEquals(8, Math.multiplyExact(2, 4));
      assertEquals(Integer.MAX_VALUE, Math.multiplyExact(Integer.MAX_VALUE, 1));
      assertEquals(Integer.MIN_VALUE, Math.multiplyExact(Integer.MIN_VALUE / 2, 2));

      try {
        throw new AssertionError(Math.multiplyExact(Integer.MAX_VALUE, 2));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.multiplyExact(Integer.MIN_VALUE, 2));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testMultiplyExactLong() {
      assertEquals(8L, Math.multiplyExact(2L, 4L));
      assertEquals(Long.MAX_VALUE, Math.multiplyExact(Long.MAX_VALUE, 1L));
      assertEquals(Long.MIN_VALUE, Math.multiplyExact(Long.MIN_VALUE / 2L, 2L));

      try {
        throw new AssertionError(Math.multiplyExact(Long.MAX_VALUE, 2L));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.multiplyExact(Long.MIN_VALUE, 2L));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testNegateExactInteger() {
      assertEquals(0, Math.negateExact(0));
      assertEquals(-1, Math.negateExact(1));
      assertEquals(1, Math.negateExact(-1));
      assertEquals(-2_147_483_647, Math.negateExact(Integer.MAX_VALUE));

      try {
        throw new AssertionError(Math.negateExact(Integer.MIN_VALUE));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testNegateExactLong() {
      assertEquals(0L, Math.negateExact(0L));
      assertEquals(-1L, Math.negateExact(1L));
      assertEquals(1L, Math.negateExact(-1L));
      assertEquals(-9_223_372_036_854_775_807L, Math.negateExact(Long.MAX_VALUE));

      try {
        throw new AssertionError(Math.negateExact(Long.MIN_VALUE));
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
        assertEquals(Math.nextAfter(interestingValue, Double.NEGATIVE_INFINITY),
            Math.nextDown(interestingValue));
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
        assertEquals(Math.nextAfter(interestingValue, Float.NEGATIVE_INFINITY),
            Math.nextDown(interestingValue));
      }
    }

    private static void testSubtractExactInteger() {
      assertEquals(-1, Math.subtractExact(0, 1));
      assertEquals(1, Math.subtractExact(0, -1));

      assertEquals(Integer.MAX_VALUE, Math.subtractExact(Integer.MAX_VALUE, 0));
      assertEquals(Integer.MIN_VALUE, Math.subtractExact(Integer.MIN_VALUE, 0));

      try {
        throw new AssertionError(Math.subtractExact(Integer.MIN_VALUE, 1));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.subtractExact(Integer.MAX_VALUE, -1));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testSubtractExactLong() {
      assertEquals(-1L, Math.subtractExact(0L, 1L));
      assertEquals(1L, Math.subtractExact(0L, -1L));

      assertEquals(Long.MAX_VALUE, Math.subtractExact(Long.MAX_VALUE, 0L));
      assertEquals(Long.MIN_VALUE, Math.subtractExact(Long.MIN_VALUE, 0L));

      try {
        throw new AssertionError(Math.subtractExact(Long.MIN_VALUE, 1L));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.subtractExact(Long.MAX_VALUE, -1L));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testToIntExact() {
      assertEquals(0, Math.toIntExact(0L));
      assertEquals(Integer.MAX_VALUE, Math.toIntExact(Integer.MAX_VALUE));
      assertEquals(Integer.MIN_VALUE, Math.toIntExact(Integer.MIN_VALUE));

      try {
        throw new AssertionError(Math.toIntExact(Integer.MAX_VALUE + 1L));
      } catch (ArithmeticException expected) {
      }
      try {
        throw new AssertionError(Math.toIntExact(Integer.MIN_VALUE - 1L));
      } catch (ArithmeticException expected) {
      }
    }
  }
}
