// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.OptionalInt;

public final class OptionalIntBackportJava9Main {

  public static void main(String[] args) {
    testIfPresentOrElseInt();
    testStreamInt();
  }

  private static void testIfPresentOrElseInt() {
    OptionalInt value = OptionalInt.of(1);
    OptionalInt emptyValue = OptionalInt.empty();
    value.ifPresentOrElse(val -> {}, () -> assertTrue(false));
    emptyValue.ifPresentOrElse(val -> assertTrue(false), () -> {});
  }

  private static void testStreamInt() {
    OptionalInt value = OptionalInt.of(2);
    OptionalInt emptyValue = OptionalInt.empty();
    assertTrue(value.stream().count() == 1);
    assertTrue(emptyValue.stream().count() == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
