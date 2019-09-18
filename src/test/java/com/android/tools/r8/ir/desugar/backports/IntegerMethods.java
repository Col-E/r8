// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class IntegerMethods {

  public static int compare(int a, int b) {
    return a == b ? 0 : a < b ? -1 : 1;
  }

  public static int divideUnsigned(int dividend, int divisor) {
    long dividendLong = dividend & 0xffffffffL;
    long divisorLong = divisor & 0xffffffffL;
    return (int) (dividendLong / divisorLong);
  }

  public static int remainderUnsigned(int dividend, int divisor) {
    long dividendLong = dividend & 0xffffffffL;
    long divisorLong = divisor & 0xffffffffL;
    return (int) (dividendLong % divisorLong);
  }

  public static int compareUnsigned(int a, int b) {
    int aFlipped = a ^ Integer.MIN_VALUE;
    int bFlipped = b ^ Integer.MIN_VALUE;
    return Integer.compare(aFlipped, bFlipped);
  }

  public static long toUnsignedLong(int value) {
    return value & 0xffffffffL;
  }

  public static int parseUnsignedInt(String s) {
    return Integer.parseUnsignedInt(s, 10);
  }

  public static int parseUnsignedIntWithRadix(String s, int radix) {
    if (s.length() > 1 && s.charAt(0) == '+') {
      // Long.parseLong on Dalvik VMs prior to 5.0 failed to handle plus sign-prefixes.
      s = s.substring(1);
    }
    long result = Long.parseLong(s, radix);
    if ((result & 0xffffffffL) != result) {
      throw new NumberFormatException(
          "Input " + s + " in base " + radix + " is not in the range of an unsigned integer");
    }
    return (int) result;
  }

  public static String toUnsignedString(int i) {
    return Integer.toUnsignedString(i, 10);
  }

  public static String toUnsignedStringWithRadix(int i, int radix) {
    long asLong = i & 0xffffffffL;
    return Long.toString(asLong, radix);
  }
}
