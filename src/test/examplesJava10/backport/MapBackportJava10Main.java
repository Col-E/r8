// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.HashMap;
import java.util.Map;

public class MapBackportJava10Main {

  public static void main(String[] args) {
    testCopyOf();
  }

  private static void testCopyOf() {
    Object key0 = new Object();
    Object value0 = new Object();
    Object key1 = new Object();
    Object value1 = new Object();
    Map<Object, Object> original = new HashMap<>();
    original.put(key0, value0);
    original.put(key1, value1);
    Map<Object, Object> copy = Map.copyOf(original);
    assertEquals(2, copy.size());
    assertEquals(original, copy);
    assertSame(value0, copy.get(key0));
    assertSame(value1, copy.get(key1));
    assertMutationNotAllowed(copy);

    // Mutate the original backing collection and ensure it's not reflected in copy.
    original.put(key0, new Object());
    assertSame(value0, copy.get(key0));

    try {
      Map.copyOf(null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map<Object, Object> map = new HashMap<>();
      map.put(null, new Object());
      Map.copyOf(map);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map<Object, Object> map = new HashMap<>();
      map.put(new Object(), null);
      Map.copyOf(map);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void assertMutationNotAllowed(Map<Object, Object> ofObject) {
    try {
      ofObject.put(new Object(), new Object());
      throw new AssertionError();
    } catch (UnsupportedOperationException expected) {
    }
    for (Map.Entry<Object, Object> entry : ofObject.entrySet()) {
      try {
        entry.setValue(new Object());
        throw new AssertionError();
      } catch (UnsupportedOperationException expected) {
      }
    }
  }

  private static void assertSame(Object expected, Object actual) {
    if (expected != actual) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
    }
  }

  private static void assertEquals(Object expected, Object actual) {
    if (expected != actual && !expected.equals(actual)) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
    }
  }
}
