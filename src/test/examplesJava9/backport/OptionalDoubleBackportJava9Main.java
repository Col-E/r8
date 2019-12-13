// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.OptionalDouble;

public final class OptionalDoubleBackportJava9Main {

  public static void main(String[] args) {
    testIfPresentOrElseDouble();
    testStreamDouble();
  }

  private static void testIfPresentOrElseDouble() {
    OptionalDouble value = OptionalDouble.of(1.0d);
    OptionalDouble emptyValue = OptionalDouble.empty();
    value.ifPresentOrElse(val -> {}, () -> assertTrue(false));
    emptyValue.ifPresentOrElse(val -> assertTrue(false), () -> {});
  }

  private static void testStreamDouble() {
    OptionalDouble value = OptionalDouble.of(2d);
    OptionalDouble emptyValue = OptionalDouble.empty();
    assertTrue(value.stream().count() == 1);
    assertTrue(emptyValue.stream().count() == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
