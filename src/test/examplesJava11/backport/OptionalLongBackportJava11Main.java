// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.OptionalLong;

public final class OptionalLongBackportJava11Main {

  public static void main(String[] args) {
    testIsEmpty();
  }

  private static void testIsEmpty() {
    OptionalLong present = OptionalLong.of(2L);
    assertFalse(present.isEmpty());

    OptionalLong absent = OptionalLong.empty();
    assertTrue(absent.isEmpty());
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }

  private static void assertFalse(boolean value) {
    if (value) {
      throw new AssertionError("Expected <false> but was <true>");
    }
  }
}
