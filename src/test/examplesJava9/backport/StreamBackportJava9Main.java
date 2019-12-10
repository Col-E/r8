// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.stream.Stream;

public final class StreamBackportJava9Main {

  public static void main(String[] args) {
    testOfNullable();
  }

  public static void testOfNullable() {
    Object guineaPig = new Object();
    Stream<Object> streamNonEmpty = Stream.ofNullable(guineaPig);
    assertTrue(streamNonEmpty.count() == 1);
    Stream<Object> streamEmpty = Stream.ofNullable(null);
    assertTrue(streamEmpty.count() == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
