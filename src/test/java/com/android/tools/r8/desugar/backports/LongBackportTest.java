// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.math.BigInteger;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class LongBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public LongBackportTest(TestParameters parameters) {
    super(parameters, Long.class, Main.class);
    registerTarget(AndroidApiLevel.O, 47);
    registerTarget(AndroidApiLevel.N, 11);
    registerTarget(AndroidApiLevel.K, 7);
  }

  static final class Main extends MiniAssert {
    private static final long[] interestingValues = {
        Long.MIN_VALUE, Long.MAX_VALUE,
        Integer.MIN_VALUE, Integer.MAX_VALUE,
        Short.MIN_VALUE, Short.MAX_VALUE,
        Byte.MIN_VALUE, Byte.MAX_VALUE,
        0L,
        -1L, 1L,
        -42L, 42L
    };

    public static void main(String[] args) {
      testHashCode();
      testCompare();
      testMax();
      testMin();
      testSum();
      testCompareUnsigned();
      testDivideUnsigned();
      testRemainderUnsigned();
      testParseUnsignedLong();
      testParseUnsignedLongWithRadix();
      testToUnsignedString();
      testToUnsignedStringWithRadix();
    }

    private static void testHashCode() {
      for (long value : interestingValues) {
        assertEquals(expectedHashCode(value), Long.hashCode(value));
      }
    }

    private static void testCompare() {
      assertTrue(Long.compare(1, 0) > 0);
      assertTrue(Long.compare(0, 0) == 0);
      assertTrue(Long.compare(0, 1) < 0);
      assertTrue(Long.compare(Long.MIN_VALUE, Long.MAX_VALUE) < 0);
      assertTrue(Long.compare(Long.MAX_VALUE, Long.MIN_VALUE) > 0);
      assertTrue(Long.compare(Long.MIN_VALUE, Long.MIN_VALUE) == 0);
      assertTrue(Long.compare(Long.MAX_VALUE, Long.MAX_VALUE) == 0);
    }

    private static void testMax() {
      for (long x : interestingValues) {
        for (long y : interestingValues) {
          assertEquals(Math.max(x, y), Long.max(x, y));
        }
      }
    }

    private static void testMin() {
      for (long x : interestingValues) {
        for (long y : interestingValues) {
          assertEquals(Math.min(x, y), Long.min(x, y));
        }
      }
    }

    private static void testSum() {
      for (long x : interestingValues) {
        for (long y : interestingValues) {
          assertEquals(x + y, Long.sum(x, y));
        }
      }
    }

    private static void testCompareUnsigned() {
      assertTrue(Long.compareUnsigned(0, 0) == 0);
      assertTrue(Long.compareUnsigned(0, Long.MAX_VALUE) < 0);
      assertTrue(Long.compareUnsigned(0, Long.MIN_VALUE) < 0);
      assertTrue(Long.compareUnsigned(0, -1) < 0);

      assertTrue(Long.compareUnsigned(Long.MAX_VALUE, 0) > 0);
      assertTrue(Long.compareUnsigned(Long.MAX_VALUE, Long.MAX_VALUE) == 0);
      assertTrue(Long.compareUnsigned(Long.MAX_VALUE, Long.MIN_VALUE) < 0);
      assertTrue(Long.compareUnsigned(Long.MAX_VALUE, -1) < 0);

      assertTrue(Long.compareUnsigned(Long.MIN_VALUE, 0) > 0);
      assertTrue(Long.compareUnsigned(Long.MIN_VALUE, Long.MAX_VALUE) > 0);
      assertTrue(Long.compareUnsigned(Long.MIN_VALUE, Long.MIN_VALUE) == 0);
      assertTrue(Long.compareUnsigned(Long.MIN_VALUE, -1) < 0);

      assertTrue(Long.compareUnsigned(-1, 0) > 0);
      assertTrue(Long.compareUnsigned(-1, Long.MAX_VALUE) > 0);
      assertTrue(Long.compareUnsigned(-1, Long.MIN_VALUE) > 0);
      assertTrue(Long.compareUnsigned(-1, -1) == 0);
    }

    private static void testDivideUnsigned() {
      for (long x : interestingValues) {
        for (long y : interestingValues) {
          if (y == 0L) continue;

          BigInteger xUnsigned = unsignedLongToBigInteger(x);
          BigInteger yUnsigned = unsignedLongToBigInteger(y);
          long expected = xUnsigned.divide(yUnsigned).longValue();

          assertEquals(expected, Long.divideUnsigned(x, y));
        }
      }

      try {
        throw new AssertionError(Long.divideUnsigned(1L, 0L));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testRemainderUnsigned() {
      for (long x : interestingValues) {
        for (long y : interestingValues) {
          if (y == 0L) continue;

          BigInteger xUnsigned = unsignedLongToBigInteger(x);
          BigInteger yUnsigned = unsignedLongToBigInteger(y);
          long expected = xUnsigned.remainder(yUnsigned).longValue();

          assertEquals(expected, Long.remainderUnsigned(x, y));
        }
      }

      try {
        throw new AssertionError(Long.remainderUnsigned(1L, 0L));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testParseUnsignedLong() {
      for (long value : interestingValues) {
        String valueString = unsignedLongToBigInteger(value).toString();
        assertEquals(value, Long.parseUnsignedLong(valueString));
        assertEquals(value, Long.parseUnsignedLong("+" + valueString));
      }

      try {
        throw new AssertionError(Long.parseUnsignedLong("18446744073709551616"));
      } catch (NumberFormatException expected) {
      }
      try {
        throw new AssertionError(Long.parseUnsignedLong(""));
      } catch (NumberFormatException expected) {
      }
      try {
        throw new AssertionError(Long.parseUnsignedLong("+"));
      } catch (NumberFormatException expected) {
      }
    }

    private static void testParseUnsignedLongWithRadix() {
      for (long value : interestingValues) {
        for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
          String valueString = unsignedLongToBigInteger(value).toString(radix);
          assertEquals(value, Long.parseUnsignedLong(valueString, radix));
          assertEquals(value, Long.parseUnsignedLong("+" + valueString, radix));
        }
      }

      try {
        throw new AssertionError(Long.parseUnsignedLong("0", Character.MIN_RADIX - 1));
      } catch (IllegalArgumentException expected) {
      }
      try {
        throw new AssertionError(Long.parseUnsignedLong("0", Character.MAX_RADIX + 1));
      } catch (IllegalArgumentException expected) {
      }

      try {
        throw new AssertionError(Long.parseUnsignedLong("", 16));
      } catch (NumberFormatException expected) {
      }
      try {
        throw new AssertionError(Long.parseUnsignedLong("+", 16));
      } catch (NumberFormatException expected) {
      }

      BigInteger overflow = new BigInteger("18446744073709551616");
      for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
        String overflowString = overflow.toString(radix);
        try {
          throw new AssertionError(Long.parseUnsignedLong(overflowString, radix));
        } catch (NumberFormatException expected) {
        }
      }
    }

    private static void testToUnsignedString() {
      for (long value : interestingValues) {
        assertEquals(unsignedLongToBigInteger(value).toString(), Long.toUnsignedString(value));
      }
    }

    private static void testToUnsignedStringWithRadix() {
      for (long value : interestingValues) {
        for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
          assertEquals(unsignedLongToBigInteger(value).toString(radix),
              Long.toUnsignedString(value, radix));
        }

        // Invalid radix values are ignored and 10 is used.
        assertEquals(unsignedLongToBigInteger(value).toString(),
            Long.toUnsignedString(value, Character.MIN_RADIX - 1));
        assertEquals(unsignedLongToBigInteger(value).toString(),
            Long.toUnsignedString(value, Character.MAX_RADIX + 1));
      }
    }

    @IgnoreInvokes
    private static int expectedHashCode(long value) {
      return Long.valueOf(value).hashCode();
    }

    private static BigInteger unsignedLongToBigInteger(long value) {
      BigInteger bigInt = BigInteger.valueOf(value & 0x7fffffffffffffffL);
      if (value < 0) {
        bigInt = bigInt.setBit(Long.SIZE - 1);
      }
      return bigInt;
    }
  }
}
