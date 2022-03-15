// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.Objects;
import java.util.function.Predicate;

public final class PredicateBackportJava11Main {
  public static void main(String[] args) {
    testNot();
  }

  private static void testNot() {

    Predicate<Object> isNull = Objects::isNull;
    Predicate<Object> notNull = Predicate.not(isNull);

    assertEquals(notNull.test(null), false);
    assertEquals(notNull.test("something"), true);

    try {
      Predicate.not(null);
      throw new AssertionError("Expected to throw NPE");
    } catch (Throwable t) {
      // Expected.
    }
  }

  private static void assertEquals(Object expected, Object actual) {
    if (expected != actual && (expected == null || !expected.equals(actual))) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
