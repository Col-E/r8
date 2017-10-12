// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package stringconcat;

public class StringConcat {
  private static void check(String actual, String expected) {
    if (expected.equals(actual)) {
      return;
    }
    throw new AssertionError(
        "Test method failed: expected=[" + expected + "], actual=[" + actual + "]");
  }

  // --------- used 'makeConcat' signatures ---------

  private static String makeConcat() {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(String s) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(char[] s) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(Object o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(boolean o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(char o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(byte o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(short o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(int o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(long o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(float o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(double o) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcat(Object o, String st, boolean z,
      char c, byte b, short s, int i, long l, float f, double d) {
    throw new AssertionError("unreachable");
  }

  // --------- used 'makeConcatWithConstants' signatures ---------

  private static String makeConcatWithConstants(String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(String s, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(char[] s, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(Object o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(boolean o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(char o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(byte o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(short o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(int o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(long o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(float o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(double o, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(Object o, String st, boolean z,
      char c, byte b, short s, int i, long l, float f, double d, String recipe) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(int i, String s, String recipe, String sConst) {
    throw new AssertionError("unreachable");
  }

  private static String makeConcatWithConstants(
      int i, String s, String recipe, String sConstA, String sConstB, String sConstC) {
    throw new AssertionError("unreachable");
  }

  // ------------------------------------------------

  private static void testEmpty() {
    check(makeConcat(), "");
    makeConcat();

    check(makeConcatWithConstants("RECIPE:"), "");
    check(makeConcatWithConstants("RECIPE:12-34"), "12-34");
    makeConcatWithConstants("RECIPE:a");
  }

  private static void testSingleValueString() {
    check(makeConcat("str"), "str");
    check(makeConcat((String) null), "null");

    check(makeConcatWithConstants("()", "RECIPE:prefix\u0001suffix"), "prefix()suffix");
    check(makeConcatWithConstants("()", "RECIPE:prefix\u0001"), "prefix()");
    check(makeConcatWithConstants("()", "RECIPE:\u0001suffix"), "()suffix");
    check(makeConcatWithConstants("()", "RECIPE:\u0001"), "()");
  }

  private static void testSingleValueArray() {
    // Unchecked since Array.toString() is non-deterministic.
    makeConcat(new char[] { 'a', 'b' });
    makeConcatWithConstants(new char[] { 'a', 'b' }, "RECIPE:prefix\u0001suffix");
  }

  private static void testSingleValueObject() {
    check(makeConcat((Object) "object"), "object");
    check(makeConcat((Object) 1.234), "1.234");
    check(makeConcat((Object) null), "null");

    check(
        makeConcatWithConstants((Object) "object", "RECIPE:prefix\u0001suffix"),
        "prefixobjectsuffix");
    check(
        makeConcatWithConstants((Object) 1.234, "RECIPE:prefix\u0001suffix"),
        "prefix1.234suffix");
    check(
        makeConcatWithConstants((Object) null, "RECIPE:prefix\u0001suffix"),
        "prefixnullsuffix");
  }

  private static void testSingleValuePrimitive() {
    check(makeConcat(true), "true");
    check(makeConcat((char) 65), "A");
    check(makeConcat((byte) 1), "1");
    check(makeConcat((short) 2), "2");
    check(makeConcat(3), "3");
    check(makeConcat((long) 4), "4");
    check(makeConcat((float) 5), "5.0");
    check(makeConcat((double) 6), "6.0");

    check(makeConcatWithConstants(true, "RECIPE:prefix\u0001suffix"), "prefixtruesuffix");
    check(makeConcatWithConstants((char) 65, "RECIPE:prefix\u0001suffix"), "prefixAsuffix");
    check(makeConcatWithConstants((byte) 1, "RECIPE:prefix\u0001suffix"), "prefix1suffix");
    check(makeConcatWithConstants((short) 2, "RECIPE:prefix\u0001suffix"), "prefix2suffix");
    check(makeConcatWithConstants(3, "RECIPE:prefix\u0001suffix"), "prefix3suffix");
    check(makeConcatWithConstants((long) 4, "RECIPE:prefix\u0001suffix"), "prefix4suffix");
    check(makeConcatWithConstants((float) 5, "RECIPE:prefix\u0001suffix"), "prefix5.0suffix");
    check(makeConcatWithConstants((double) 6, "RECIPE:prefix\u0001suffix"), "prefix6.0suffix");
  }

  private static void testAllTypes(Object o, String st, boolean z,
      char c, byte b, short s, int i, long l, float f, double d) {
    check(makeConcat(o, st, z, c, b, s, i, l, f, d), "nullstrtrueA12345.06.0");
    check(makeConcatWithConstants(o, st, z, c, b, s, i, l, f, d,
        "RECIPE:[\u0001-\u0001>\u0001===\u0001\u0001\u0001alpha\u0001beta\u0001\u0001]\u0001"),
        "[null-str>true===A12alpha3beta45.0]6.0");
  }

  private static void testInExceptionContext(Object o, String st, boolean z,
      char c, byte b, short s, int i, long l, float f, double d) {
    check(makeConcat((long) 4), "4");
    try {
      check(makeConcat(o, st, z, c, b, s, i, l, f, d), "nullstrtrueA12345.06.0");
      check(makeConcatWithConstants(o, st, z, c, b, s, i, l, f, d,
          "RECIPE:\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"),
          "nullstrtrueA12345.06.0");
      try {
        check(makeConcat("try-try"), "try-try");
        throw new IndexOutOfBoundsException();
      } catch (NullPointerException re) {
        throw new AssertionError("UNREACHABLE");
      } catch (Exception re) {
        check(makeConcatWithConstants(o, st, z, c, b, s, i, l, f, d,
            "RECIPE:(\u0001, \u0001, \u0001, \u0001, \u0001, "
                + "\u0001, \u0001, \u0001, \u0001, \u0001)"),
            "(null, str, true, A, 1, 2, 3, 4, 5.0, 6.0)");
        throw new IndexOutOfBoundsException();
      }
    } catch (IndexOutOfBoundsException re) {
      check(makeConcat("bar"), "bar");
      check(makeConcatWithConstants("foo", "RECIPE:bar -> \u0001"), "bar -> foo");
      try {
        check(makeConcatWithConstants("inside", "RECIPE:try \u0001 try"), "try inside try");
        throw new NullPointerException();
      } catch (IndexOutOfBoundsException e) {
        throw new AssertionError("UNREACHABLE");
      } catch (NullPointerException npe) {
        check(makeConcat(o, st, z, c, b, s, i, l, f, d), "nullstrtrueA12345.06.0");
      }
    } catch (Exception re) {
      throw new AssertionError("UNREACHABLE");
    }
  }

  private static void testConcatWitConstants() {
    check(
        makeConcatWithConstants(
            123, "abc", "RECIPE:arg=\u0001; const=\u0002; arg=\u0001", "str"
        ),
        "arg=123; const=str; arg=abc");
    check(
        makeConcatWithConstants(
            123, "abc", "RECIPE:\u0002arg=\u0001\u0002arg=\u0001\u0002",
            "prefix-", "-infix-", "-suffix"
        ),
        "prefix-arg=123-infix-arg=abc-suffix");
  }

  // ------------------------------------------------

  public static void main(String[] args) {
    testEmpty();
    testSingleValueString();
    testSingleValueArray();
    testSingleValueObject();
    testSingleValuePrimitive();
    testAllTypes(null, "str", true, (char) 65,
        (byte) 1, (short) 2, 3, (long) 4, (float) 5, (double) 6);
    testInExceptionContext(null, "str", true, (char) 65,
        (byte) 1, (short) 2, 3, (long) 4, (float) 5, (double) 6);
    testConcatWitConstants();
  }
}
