// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class LongMethods extends TemplateMethodCode {
  public LongMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(long l) {
    return (int) (l ^ (l >>> 32));
  }

  public static long max(long a, long b) {
    return Math.max(a, b);
  }

  public static long min(long a, long b) {
    return Math.min(a, b);
  }

  public static long sum(long a, long b) {
    return a + b;
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
}
