// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.Optional;

public final class OptionalBackportJava9Main {

  public static void main(String[] args) {
    testOr();
    testOrNull();
    testIfPresentOrElse();
    testStream();
  }

  private static void testOr() {
    Optional<String> value = Optional.of("value");
    Optional<String> defaultValue = Optional.of("default");
    Optional<String> emptyValue = Optional.empty();
    Optional<String> result;

    result = value.or(() -> defaultValue);
    assertTrue(value == result);
    result = emptyValue.or(() -> defaultValue);
    assertTrue(result == defaultValue);
  }

  private static void testOrNull() {
    Optional<String> value = Optional.of("value");
    Optional<String> emptyValue = Optional.empty();

    try {
      value.or(null);
      fail();
    } catch (NullPointerException e) {
    }

    try {
      emptyValue.or(null);
      fail();
    } catch (NullPointerException e) {
    }

    try {
      value.or(() -> null);
    } catch (NullPointerException e) {
      fail();
    }

    try {
      emptyValue.or(() -> null);
      fail();
    } catch (NullPointerException e) {
    }
  }

  private static void testIfPresentOrElse() {
    Optional<String> value = Optional.of("value");
    Optional<String> emptyValue = Optional.empty();
    value.ifPresentOrElse(val -> {}, () -> assertTrue(false));
    emptyValue.ifPresentOrElse(val -> assertTrue(false), () -> {});
  }

  private static void testStream() {
    Optional<String> value = Optional.of("value");
    Optional<String> emptyValue = Optional.empty();
    assertTrue(value.stream().count() == 1);
    assertTrue(emptyValue.stream().count() == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }

  private static void fail() {
    throw new AssertionError("Failure.");
  }
}
