// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.math.BigInteger;

public final class IntegerBackportJava9Main {
  private static final int[] interestingValues = {
    Integer.MIN_VALUE,
    Integer.MAX_VALUE,
    Short.MIN_VALUE,
    Short.MAX_VALUE,
    Byte.MIN_VALUE,
    Byte.MAX_VALUE,
    0,
    -1,
    1,
    -42,
    42
  };

  public static void main(String[] args) {
    testParseIntegerSubsequenceWithRadix();
  }

  private static void testParseIntegerSubsequenceWithRadix() {
    for (int value : interestingValues) {
      for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
        for (String prefix : new String[] {"", "x", "xxx"}) {
          for (String postfix : new String[] {"", "x", "xxx"}) {
            String valueString = prefix + Long.toString(value, radix) + postfix;
            int start = prefix.length();
            int end = valueString.length() - postfix.length();
            assertEquals(valueString, value, Integer.parseInt(valueString, start, end, radix));
            if (value > 0) {
              valueString = prefix + '+' + Long.toString(value, radix) + postfix;
              end++;
              assertEquals(valueString, value, Integer.parseInt(valueString, start, end, radix));
            }
          }
        }
      }
    }

    try {
      throw new AssertionError(Integer.parseInt("0", 0, 1, Character.MIN_RADIX - 1));
    } catch (IllegalArgumentException expected) {
    }
    try {
      throw new AssertionError(Integer.parseInt("0", 0, 1, Character.MAX_RADIX + 1));
    } catch (IllegalArgumentException expected) {
    }

    try {
      throw new AssertionError(Integer.parseInt("", 0, 0, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Integer.parseInt("-", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Integer.parseInt("+", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }

    try {
      throw new AssertionError(Integer.parseInt("+a", 0, 1, 10));
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
          String underflowString = prefix + '-' + overflow.toString(radix) + postfix;
          start = prefix.length();
          end = underflowString.length() - postfix.length();
          try {
            throw new AssertionError(Long.parseLong(underflowString, start, end, radix));
          } catch (NumberFormatException expected) {
          }
        }
      }
    }
  }

  private static void assertEquals(String m, int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError(m + " Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
