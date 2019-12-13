// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.OptionalLong;

public final class OptionalLongBackportJava9Main {

  public static void main(String[] args) {
    testIfPresentOrElseLong();
    testStreamLong();
  }

  private static void testIfPresentOrElseLong() {
    OptionalLong value = OptionalLong.of(1L);
    OptionalLong emptyValue = OptionalLong.empty();
    value.ifPresentOrElse(val -> {}, () -> assertTrue(false));
    emptyValue.ifPresentOrElse(val -> assertTrue(false), () -> {});
  }

  private static void testStreamLong() {
    OptionalLong value = OptionalLong.of(2L);
    OptionalLong emptyValue = OptionalLong.empty();
    assertTrue(value.stream().count() == 1);
    assertTrue(emptyValue.stream().count() == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
