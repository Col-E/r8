// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.Objects;

public class ObjectsBackportJava17Main {

  public static void main(String[] args) {
    // The methods are actually from Java 16, but we can test them from Java 17.
    testCheckIndex();
    testCheckFromToIndex();
    testCheckFromIndexSize();
  }

  private static void testCheckIndex() {
    for (long i = 0L; i < 10L; i++) {
      assertEquals(i, Objects.checkIndex(i, 10L));
    }

    try {
      throw new AssertionError(Objects.checkIndex(-1L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkIndex(10L, 0L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkIndex(0L, 0L));
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  private static void testCheckFromToIndex() {
    for (long i = 0L; i <= 10L; i++) {
      for (long j = i; j <= 10L; j++) {
        assertEquals(i, Objects.checkFromToIndex(i, j, 10L));
      }
    }
    assertEquals(0L, Objects.checkFromToIndex(0L, 0L, 0L));

    try {
      throw new AssertionError(Objects.checkFromToIndex(4L, 2L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(-1L, 5L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(0L, -1L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(11L, 11L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(0L, 1L, 0L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(1L, 1L, 0L));
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  private static void testCheckFromIndexSize() {
    for (long i = 0L; i <= 10L; i++) {
      for (long j = 10L - i; j >= 0L; j--) {
        assertEquals(i, Objects.checkFromIndexSize(i, j, 10L));
      }
    }
    assertEquals(0, Objects.checkFromIndexSize(0L, 0L, 0L));

    try {
      throw new AssertionError(Objects.checkFromIndexSize(8L, 4L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(-1L, 5L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(11L, 0L, 10L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(0L, 1L, 0L));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(1L, 1L, 0L));
    } catch (IndexOutOfBoundsException expected) {
    }

    // Check for cases where overflow might occur producing incorrect results.
    try {
      throw new AssertionError(Objects.checkFromIndexSize(Long.MAX_VALUE, 1L, Long.MAX_VALUE));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(0L, 1L, Long.MIN_VALUE));
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  private static void assertEquals(long expected, long actual) {
    if (expected != actual) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
