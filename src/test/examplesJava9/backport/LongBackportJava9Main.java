// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.math.BigInteger;

public final class LongBackportJava9Main {
  private static final long[] interestingValues = {
    Long.MIN_VALUE,
    Long.MAX_VALUE,
    Integer.MIN_VALUE,
    Integer.MAX_VALUE,
    Short.MIN_VALUE,
    Short.MAX_VALUE,
    Byte.MIN_VALUE,
    Byte.MAX_VALUE,
    0L,
    -1L,
    1L,
    -42L,
    42L
  };

  public static void main(String[] args) {
    testParseLongSubsequenceWithRadix();
    testParseUnsignedLongSubsequenceWithRadix();
  }

  private static void testParseLongSubsequenceWithRadix() {
    for (long value : interestingValues) {
      for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
        for (String prefix : new String[] {"", "x", "xxx"}) {
          for (String postfix : new String[] {"", "x", "xxx"}) {
            String valueString = prefix + Long.toString(value, radix) + postfix;
            int start = prefix.length();
            int end = valueString.length() - postfix.length();
            assertEquals(valueString, value, Long.parseLong(valueString, start, end, radix));
            if (value > 0) {
              valueString = prefix + "+" + Long.toString(value, radix) + postfix;
              end++;
              assertEquals(valueString, value, Long.parseLong(valueString, start, end, radix));
            }
          }
        }
      }
    }

    try {
      throw new AssertionError(Long.parseLong("0", 0, 1, Character.MIN_RADIX - 1));
    } catch (IllegalArgumentException expected) {
    }
    try {
      throw new AssertionError(Long.parseLong("0", 0, 1, Character.MAX_RADIX + 1));
    } catch (IllegalArgumentException expected) {
    }

    try {
      throw new AssertionError(Long.parseLong("", 0, 0, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Long.parseLong("-", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Long.parseLong("+", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }

    try {
      throw new AssertionError(Long.parseLong("+a", 0, 2, 10));
    } catch (NumberFormatException expected) {
    }

    BigInteger overflow = new BigInteger("18446744073709551616");
    for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
      for (String prefix : new String[] {"", "x", "xxx"}) {
        for (String postfix : new String[] {"", "x", "xxx"}) {
          String overflowString = prefix + overflow.toString(radix) + postfix;
          int start = prefix.length();
          int end = overflowString.length() - postfix.length();
          try {
            throw new AssertionError(Long.parseLong(overflowString, start, end, radix));
          } catch (NumberFormatException expected) {
          }
        }
      }
    }
  }

  private static void testParseUnsignedLongSubsequenceWithRadix() {
    for (long value : interestingValues) {
      for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
        for (String prefix : new String[] {"", "x", "xxx"}) {
          for (String postfix : new String[] {"", "x", "xxx"}) {
            String valueString = prefix + unsignedLongToBigInteger(value).toString(radix) + postfix;
            int start = prefix.length();
            int end = valueString.length() - postfix.length();
            assertEquals(
                valueString, value, Long.parseUnsignedLong(valueString, start, end, radix));
            valueString = prefix + "+" + unsignedLongToBigInteger(value).toString(radix) + postfix;
            end++;
            assertEquals(
                valueString, value, Long.parseUnsignedLong(valueString, start, end, radix));
          }
        }
      }
    }

    try {
      throw new AssertionError(Long.parseUnsignedLong("0", 0, 1, Character.MIN_RADIX - 1));
    } catch (IllegalArgumentException expected) {
    }
    try {
      throw new AssertionError(Long.parseUnsignedLong("0", 0, 1, Character.MAX_RADIX + 1));
    } catch (IllegalArgumentException expected) {
    }

    try {
      throw new AssertionError(Long.parseUnsignedLong("", 0, 0, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Long.parseUnsignedLong("+", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }

    try {
      throw new AssertionError(Long.parseUnsignedLong("+a", 0, 2, 10));
    } catch (NumberFormatException expected) {
    }

    BigInteger overflow = new BigInteger("18446744073709551616");
    for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
      for (String prefix : new String[] {"", "x", "xxx", "+", "x+", "xxx+"}) {
        for (String postfix : new String[] {"", "x", "xxx"}) {
          String overflowString = prefix + overflow.toString(radix) + postfix;
          int start = prefix.length();
          int end = overflowString.length() - postfix.length();
          try {
            throw new AssertionError(Long.parseUnsignedLong(overflowString, start, end, radix));
          } catch (NumberFormatException expected) {
          }
        }
      }
    }
  }

  private static BigInteger unsignedLongToBigInteger(long value) {
    BigInteger bigInt = BigInteger.valueOf(value & 0x7fffffffffffffffL);
    if (value < 0) {
      bigInt = bigInt.setBit(Long.SIZE - 1);
    }
    return bigInt;
  }

  private static void assertEquals(String m, long expected, long actual) {
    if (expected != actual) {
      throw new AssertionError(m + " Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
