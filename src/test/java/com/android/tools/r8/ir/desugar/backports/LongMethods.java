// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class LongMethods {

  public static int hashCode(long l) {
    return (int) (l ^ (l >>> 32));
  }

  public static long divideUnsigned(long dividend, long divisor) {
    // This implementation is adapted from Guava's UnsignedLongs.java and Longs.java.

    if (divisor < 0) { // i.e., divisor >= 2^63:
      // Reference implementation calls UnsignedLongs.compare(dividend, divisor) whose
      // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
      // implementations of flip() and compare() are inlined here instead.
      long dividendFlipped = dividend ^ Long.MIN_VALUE;
      long divisorFlipped = divisor ^ Long.MIN_VALUE;
      if (dividendFlipped < divisorFlipped) {
        return 0; // dividend < divisor
      } else {
        return 1; // dividend >= divisor
      }
    }

    // Optimization - use signed division if dividend < 2^63
    if (dividend >= 0) {
      return dividend / divisor;
    }

    // Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
    // guaranteed to be either exact or one less than the correct value. This follows from the
    // fact that floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is
    // not quite trivial.
    long quotient = ((dividend >>> 1) / divisor) << 1;
    long rem = dividend - quotient * divisor;

    // Reference implementation calls UnsignedLongs.compare(rem, divisor) whose
    // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
    // implementations of flip() and compare() are inlined here instead.
    long remFlipped = rem ^ Long.MIN_VALUE;
    long divisorFlipped = divisor ^ Long.MIN_VALUE;
    return quotient + (remFlipped >= divisorFlipped ? 1 : 0);
  }

  public static long remainderUnsigned(long dividend, long divisor) {
    // This implementation is adapted from Guava's UnsignedLongs.java and Longs.java.

    if (divisor < 0) { // i.e., divisor >= 2^63:
      // Reference implementation calls UnsignedLongs.compare(dividend, divisor) whose
      // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
      // implementations of flip() and compare() are inlined here instead.
      long dividendFlipped = dividend ^ Long.MIN_VALUE;
      long divisorFlipped = divisor ^ Long.MIN_VALUE;
      if (dividendFlipped < divisorFlipped) {
        return dividend; // dividend < divisor
      } else {
        return dividend - divisor; // dividend >= divisor
      }
    }

    // Optimization - use signed modulus if dividend < 2^63
    if (dividend >= 0) {
      return dividend % divisor;
    }

    // Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
    // guaranteed to be either exact or one less than the correct value. This follows from the
    // fact that floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is
    // not quite trivial.
    long quotient = ((dividend >>> 1) / divisor) << 1;
    long rem = dividend - quotient * divisor;

    // Reference implementation calls UnsignedLongs.compare(rem, divisor) whose
    // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
    // implementations of flip() and compare() are inlined here instead.
    long remFlipped = rem ^ Long.MIN_VALUE;
    long divisorFlipped = divisor ^ Long.MIN_VALUE;
    return rem - (remFlipped >= divisorFlipped ? divisor : 0);
  }

  public static int compareUnsigned(long a, long b) {
    long aFlipped = a ^ Long.MIN_VALUE;
    long bFlipped = b ^ Long.MIN_VALUE;
    return Long.compare(aFlipped, bFlipped);
  }

  public static long parseUnsignedLong(String s) {
    return Long.parseUnsignedLong(s, 10);
  }

  public static long parseUnsignedLongWithRadix(String s, int radix) {
    // This implementation is adapted from Guava's UnsignedLongs.java

    int length = s.length();
    if (length == 0) {
      throw new NumberFormatException("empty string");
    }
    if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
      // Explicit String.concat to work around https://issuetracker.google.com/issues/136596951.
      throw new NumberFormatException("illegal radix: ".concat(String.valueOf(radix)));
    }
    long maxValueBeforeRadixMultiply = Long.divideUnsigned(-1L, radix);

    // If the string starts with '+' and contains at least two characters, skip the plus.
    int start = s.charAt(0) == '+' && length > 1 ? 1 : 0;

    long value = 0;
    for (int pos = start; pos < length; pos++) {
      int digit = Character.digit(s.charAt(pos), radix);
      if (digit == -1) {
        throw new NumberFormatException(s);
      }
      if (// high bit is already set
          value < 0
          // or radix multiply will overflow
          || value > maxValueBeforeRadixMultiply
          // or digit add will overflow after radix multiply
          || (value == maxValueBeforeRadixMultiply
              && digit > (int) Long.remainderUnsigned(-1L, radix))) {
        // Explicit String.concat to work around https://issuetracker.google.com/issues/136596951.
        throw new NumberFormatException("Too large for unsigned long: ".concat(s));
      }
      value = (value * radix) + digit;
    }

    return value;
  }

  public static String toUnsignedString(long l) {
    return Long.toUnsignedString(l, 10);
  }

  public static String toUnsignedStringWithRadix(long l, int radix) {
    // This implementation is adapted from Guava's UnsignedLongs.java

    if (l == 0) {
      // Simply return "0"
      return "0";
    } else if (l > 0) {
      return Long.toString(l, radix);
    } else {
      if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
        radix = 10;
      }
      char[] buf = new char[64];
      int i = buf.length;
      if ((radix & (radix - 1)) == 0) {
        // Radix is a power of two so we can avoid division.
        int shift = Integer.numberOfTrailingZeros(radix);
        int mask = radix - 1;
        do {
          buf[--i] = Character.forDigit(((int) l) & mask, radix);
          l >>>= shift;
        } while (l != 0);
      } else {
        // Separate off the last digit using unsigned division. That will leave
        // a number that is nonnegative as a signed integer.
        long quotient;
        if ((radix & 1) == 0) {
          // Fast path for the usual case where the radix is even.
          quotient = (l >>> 1) / (radix >>> 1);
        } else {
          quotient = Long.divideUnsigned(l, radix);
        }
        long rem = l - quotient * radix;
        buf[--i] = Character.forDigit((int) rem, radix);
        l = quotient;
        // Simple modulo/division approach
        while (l > 0) {
          buf[--i] = Character.forDigit((int) (l % radix), radix);
          l /= radix;
        }
      }
      // Generate string
      return new String(buf, i, buf.length - i);
    }
  }
}
