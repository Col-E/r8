// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.NoSuchElementException;
import java.util.Optional;

public final class OptionalBackportJava10Main {

  public static void main(String[] args) {
    testOrElseThrow();
  }

  private static void testOrElseThrow() {
    Optional<String> present = Optional.of("hey");
    assertEquals("hey", present.orElseThrow());

    Optional<String> absent = Optional.empty();
    try {
      throw new AssertionError(absent.orElseThrow());
    } catch (NoSuchElementException expected) {
    }
  }

  private static void assertEquals(Object expected, Object actual) {
    if (expected != actual && !expected.equals(actual)) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
    }
  }
}
