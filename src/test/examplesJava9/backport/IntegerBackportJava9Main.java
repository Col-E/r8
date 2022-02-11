// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

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
    testParseUnsignedIntegerSubsequenceWithRadix();
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
      throw new AssertionError(Integer.parseInt("+a", 0, 2, 10));
    } catch (NumberFormatException expected) {
    }

    long overflow = 73709551616L;
    for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
      for (String prefix : new String[] {"", "x", "xxx"}) {
        for (String postfix : new String[] {"", "x", "xxx"}) {
          String overflowString = prefix + Long.toString(overflow, radix) + postfix;
          int start = prefix.length();
          int end = overflowString.length() - postfix.length();
          try {
            throw new AssertionError(Integer.parseInt(overflowString, start, end, radix));
          } catch (NumberFormatException expected) {
          }
          String underflowString = prefix + '-' + Long.toString(overflow, radix) + postfix;
          start = prefix.length();
          end = underflowString.length() - postfix.length();
          try {
            throw new AssertionError(Integer.parseInt(underflowString, start, end, radix));
          } catch (NumberFormatException expected) {
          }
        }
      }
    }
  }

  private static void testParseUnsignedIntegerSubsequenceWithRadix() {
    for (int value : interestingValues) {
      for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
        for (String prefix : new String[] {"", "x", "xxx"}) {
          for (String postfix : new String[] {"", "x", "xxx"}) {
            String unsignedIntergerString = Long.toString(Integer.toUnsignedLong(value), radix);
            String valueString = prefix + unsignedIntergerString + postfix;
            int start = prefix.length();
            int end = valueString.length() - postfix.length();
            assertEquals(
                valueString, value, Integer.parseUnsignedInt(valueString, start, end, radix));
            if (value > 0) {
              valueString = prefix + '+' + unsignedIntergerString + postfix;
              end++;
              assertEquals(
                  valueString, value, Integer.parseUnsignedInt(valueString, start, end, radix));
            }
          }
        }
      }
    }

    try {
      throw new AssertionError(Integer.parseUnsignedInt("0", 0, 1, Character.MIN_RADIX - 1));
    } catch (IllegalArgumentException expected) {
    }
    try {
      throw new AssertionError(Integer.parseUnsignedInt("0", 0, 1, Character.MAX_RADIX + 1));
    } catch (IllegalArgumentException expected) {
    }

    try {
      throw new AssertionError(Integer.parseUnsignedInt("", 0, 0, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Integer.parseUnsignedInt("-", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }
    try {
      throw new AssertionError(Integer.parseUnsignedInt("+", 0, 1, 16));
    } catch (NumberFormatException expected) {
    }

    try {
      throw new AssertionError(Integer.parseUnsignedInt("+a", 0, 2, 10));
    } catch (NumberFormatException expected) {
    }

    long overflow = 73709551616L;
    for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
      for (String prefix : new String[] {"", "x", "xxx"}) {
        for (String postfix : new String[] {"", "x", "xxx"}) {
          String overflowString = prefix + Long.toString(overflow, radix) + postfix;
          int start = prefix.length();
          int end = overflowString.length() - postfix.length();
          try {
            throw new AssertionError(Integer.parseUnsignedInt(overflowString, start, end, radix));
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
