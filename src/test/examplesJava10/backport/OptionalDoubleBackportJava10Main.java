// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.NoSuchElementException;
import java.util.OptionalDouble;

public final class OptionalDoubleBackportJava10Main {

  public static void main(String[] args) {
    testOrElseThrow();
  }

  private static void testOrElseThrow() {
    OptionalDouble present = OptionalDouble.of(2d);
    assertEquals(2d, present.orElseThrow());

    OptionalDouble absent = OptionalDouble.empty();
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
