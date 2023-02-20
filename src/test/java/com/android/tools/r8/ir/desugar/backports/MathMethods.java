// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class MathMethods {

  public static int addExactInt(int x, int y) {
    long longResult = (long) x + y;
    int intResult = (int) longResult;
    if (longResult == intResult) {
      return intResult;
    }
    throw new ArithmeticException();
  }

  public static long addExactLong(long x, long y) {
    long result = x + y;
    if ((x ^ y) < 0L | (x ^ result) >= 0L) {
      return result;
    }
    throw new ArithmeticException();
  }

  public static int decrementExactInt(int value) {
    if (value == Integer.MIN_VALUE) {
      throw new ArithmeticException();
    }
    return value - 1;
  }

  public static long decrementExactLong(long value) {
    if (value == Long.MIN_VALUE) {
      throw new ArithmeticException();
    }
    return value - 1L;
  }

  public static int floorDivInt(int dividend, int divisor) {
    int div = dividend / divisor;
    int rem = dividend - divisor * div;
    if (rem == 0) {
      return div;
    }
    // Normal Java division rounds towards 0. We just have to deal with the cases where rounding
    // towards 0 is wrong, which typically depends on the sign of dividend / divisor.
    //
    // signum is 1 if dividend and divisor are both nonnegative or negative, and -1 otherwise.
    int signum = 1 | ((dividend ^ divisor) >> (Integer.SIZE - 1));
    return signum < 0 ? div - 1 : div;
  }

  public static long floorDivLong(long dividend, long divisor) {
    long div = dividend / divisor;
    long rem = dividend - divisor * div;
    if (rem == 0L) {
      return div;
    }
    // Normal Java division rounds towards 0. We just have to deal with the cases where rounding
    // towards 0 is wrong, which typically depends on the sign of dividend / divisor.
    //
    // signum is 1 if dividend and divisor are both nonnegative or negative, and -1 otherwise.
    long signum = 1L | ((dividend ^ divisor) >> (Long.SIZE - 1));
    return signum < 0L ? div - 1L : div;
  }

  public static long floorDivLongInt(long dividend, int divisor) {
    return Math.floorDiv(dividend, (long) divisor);
  }

  public static int floorModInt(int dividend, int divisor) {
    int rem = dividend % divisor;
    if (rem == 0) {
      return 0;
    }
    // Normal Java remainder tracks the sign of the dividend. We just have to deal with the case
    // where the resulting sign is incorrect which is when the signs do not match.
    //
    // signum is 1 if dividend and divisor are both nonnegative or negative, and -1 otherwise.
    int signum = 1 | ((dividend ^ divisor) >> (Integer.SIZE - 1));
    return signum > 0 ? rem : rem + divisor;
  }

  public static long floorModLong(long dividend, long divisor) {
    long rem = dividend % divisor;
    if (rem == 0L) {
      return 0L;
    }
    // Normal Java remainder tracks the sign of the dividend. We just have to deal with the case
    // where the resulting sign is incorrect which is when the signs do not match.
    //
    // signum is 1 if dividend and divisor are both nonnegative or negative, and -1 otherwise.
    long signum = 1L | ((dividend ^ divisor) >> (Long.SIZE - 1));
    return signum > 0L ? rem : rem + divisor;
  }

  public static int floorModLongInt(long dividend, int divisor) {
    return (int) Math.floorMod(dividend, (long) divisor);
  }

  public static int incrementExactInt(int value) {
    if (value == Integer.MAX_VALUE) {
      throw new ArithmeticException();
    }
    return value + 1;
  }

  public static long incrementExactLong(long value) {
    if (value == Long.MAX_VALUE) {
      throw new ArithmeticException();
    }
    return value + 1;
  }

  public static int multiplyExactInt(int x, int y) {
    long longResult = (long) x * y;
    int intResult = (int) longResult;
    if (longResult == intResult) {
      return intResult;
    }
    throw new ArithmeticException();
  }

  public static long multiplyExactLong(long x, long y) {
    // Hacker's Delight, Section 2-12
    int leadingZeros =
        Long.numberOfLeadingZeros(x)
            + Long.numberOfLeadingZeros(~x)
            + Long.numberOfLeadingZeros(y)
            + Long.numberOfLeadingZeros(~y);

    // If leadingZeros > Long.SIZE + 1 it's definitely fine, if it's < Long.SIZE it's definitely
    // bad. We do the leadingZeros check to avoid the division below if at all possible.
    //
    // Otherwise, if y == Long.MIN_VALUE, then the only allowed values of x are 0 and 1. We take
    // care of all x < 0 with their own check, because in particular, the case x == -1 will
    // incorrectly pass the division check below.
    //
    // In all other cases, we check that either x is 0 or the result is consistent with division.
    if (leadingZeros > Long.SIZE + 1) {
      return x * y;
    }
    if (leadingZeros >= Long.SIZE && (x >= 0 | y != Long.MIN_VALUE)) {
      long result = x * y;
      if (x == 0 || result / x == y) {
        return result;
      }
    }
    throw new ArithmeticException();
  }

  public static long multiplyExactLongInt(long x, int y) {
    return Math.multiplyExact(x, (long) y);
  }

  public static long multiplyFull(int x, int y) {
    return (long) x * y;
  }

  public static long multiplyHigh(long x, long y) {
    // Adapted from Hacker's Delight (2nd ed), 8-2.
    long xLow = x & 0xFFFFFFFFL;
    long xHigh = x >> 32;
    long yLow = y & 0xFFFFFFFFL;
    long yHigh = y >> 32;

    long lowLow = xLow * yLow;
    long lowLowCarry = lowLow >>> 32;

    long highLow = xHigh * yLow;
    long mid1 = highLow + lowLowCarry;
    long mid1Low = mid1 & 0xFFFFFFFFL;
    long mid1High = mid1 >> 32;

    long lowHigh = xLow * yHigh;
    long mid2 = lowHigh + mid1Low;
    long mid2High = mid2 >> 32;

    long highHigh = xHigh * yHigh;
    return highHigh + mid1High + mid2High;
  }

  public static int negateExactInt(int value) {
    if (value == Integer.MIN_VALUE) {
      throw new ArithmeticException();
    }
    return -value;
  }

  public static long negateExactLong(long value) {
    if (value == Long.MIN_VALUE) {
      throw new ArithmeticException();
    }
    return -value;
  }

  public static double nextDownDouble(double value) {
    return -Math.nextUp(-value);
  }

  public static float nextDownFloat(float value) {
    return -Math.nextUp(-value);
  }

  public static int subtractExactInt(int x, int y) {
    long longResult = (long) x - y;
    int intResult = (int) longResult;
    if (longResult == intResult) {
      return intResult;
    }
    throw new ArithmeticException();
  }

  public static long subtractExactLong(long x, long y) {
    long result = x - y;
    if ((x ^ y) >= 0 | (x ^ result) >= 0) {
      return result;
    }
    throw new ArithmeticException();
  }

  public static int toIntExact(long value) {
    int result = (int) value;
    if (value != result) {
      throw new ArithmeticException();
    }
    return result;
  }

  public static int absExact(int a) {
    if (a == Integer.MIN_VALUE) {
      throw new ArithmeticException();
    }
    return Math.abs(a);
  }

  public static long absExactLong(long a) {
    if (a == Long.MIN_VALUE) {
      throw new ArithmeticException();
    }
    return Math.abs(a);
  }
}
