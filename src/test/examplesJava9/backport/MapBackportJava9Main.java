// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.AbstractMap;
import java.util.Map;

public class MapBackportJava9Main {

  public static void main(String[] args) {
    testOf0();
    testOf1();
    testOf2();
    testOf10();
    testOfEntries();
    testEntry();
  }

  private static void testOf0() {
    Map<Object, Object> ofObject = Map.of();
    assertEquals(0, ofObject.size());
    assertEquals(null, ofObject.get(new Object()));
    assertMutationNotAllowed(ofObject);

    Map<Integer, Integer> ofInteger = Map.of();
    assertEquals(0, ofInteger.size());
    assertEquals(null, ofInteger.get(0));
  }

  private static void testOf1() {
    Object objectKey0 = new Object();
    Object objectValue0 = new Object();
    Map<Object, Object> ofObject = Map.of(objectKey0, objectValue0);
    assertEquals(1, ofObject.size());
    assertSame(objectValue0, ofObject.get(objectKey0));
    assertEquals(null, ofObject.get(new Object()));
    assertMutationNotAllowed(ofObject);

    Map<Integer, Integer> ofInteger = Map.of(0, 0);
    assertEquals(1, ofInteger.size());
    assertEquals(0, ofInteger.get(0));
    assertEquals(null, ofInteger.get(1));

    try {
      Map.of((Object) null, 1);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map.of(1, (Object) null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void testOf2() {
    Object objectKey0 = new Object();
    Object objectValue0 = new Object();
    Object objectKey1 = new Object();
    Object objectValue1 = new Object();
    Map<Object, Object> ofObject = Map.of(objectKey0, objectValue0, objectKey1, objectValue1);
    assertEquals(2, ofObject.size());
    assertSame(objectValue0, ofObject.get(objectKey0));
    assertSame(objectValue1, ofObject.get(objectKey1));
    assertEquals(null, ofObject.get(new Object()));
    assertMutationNotAllowed(ofObject);

    Map<Integer, Integer> ofInteger = Map.of(0, 0, 1, 1);
    assertEquals(2, ofInteger.size());
    assertEquals(0, ofInteger.get(0));
    assertEquals(1, ofInteger.get(1));
    assertEquals(null, ofInteger.get(3));

    Map<Object, Object> ofMixed = Map.of(objectKey0, 0, objectKey1, 1);
    assertEquals(2, ofMixed.size());
    assertEquals(0, ofMixed.get(objectKey0));
    assertEquals(1, ofMixed.get(objectKey1));
    assertEquals(null, ofMixed.get(new Object()));

    try {
      Map.of(1, 1, null, 2);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map.of(1, 1, 2, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }

    try {
      Map.of(1, 1, 1, 2);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertEquals("duplicate key: 1", expected.getMessage());
    }
  }

  private static void testOf10() {
    Object objectKey0 = new Object();
    Object objectValue0 = new Object();
    Object objectKey6 = new Object();
    Object objectValue6 = new Object();
    Object objectKey9 = new Object();
    Object objectValue9 = new Object();
    Map<Object, Object> ofObject =
        Map.of(objectKey0, objectValue0, new Object(), new Object(), new Object(), new Object(),
            new Object(), new Object(), new Object(), new Object(), new Object(), new Object(),
            objectKey6, objectValue6, new Object(), new Object(), new Object(), new Object(),
            objectKey9, objectValue9);
    assertEquals(10, ofObject.size());
    assertSame(objectValue0, ofObject.get(objectKey0));
    assertSame(objectValue6, ofObject.get(objectKey6));
    assertSame(objectValue9, ofObject.get(objectKey9));
    assertEquals(null, ofObject.get(new Object()));
    assertMutationNotAllowed(ofObject);

    Map<Integer, Integer> ofInteger =
        Map.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9);
    assertEquals(10, ofInteger.size());
    assertEquals(0, ofInteger.get(0));
    assertEquals(6, ofInteger.get(6));
    assertEquals(9, ofInteger.get(9));
    assertEquals(null, ofInteger.get(10));

    Map<Object, Object> ofMixed =
        Map.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, objectKey9, objectValue9);
    assertEquals(10, ofMixed.size());
    assertEquals(0, ofMixed.get(0));
    assertEquals(6, ofMixed.get(6));
    assertSame(objectValue9, ofMixed.get(objectKey9));
    assertEquals(null, ofMixed.get(9));

    try {
      Map.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, null, objectValue9);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, objectKey9, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }

    try {
      Map.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 0, 9);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertEquals("duplicate key: 0", expected.getMessage());
    }
  }

  private static void testOfEntries() {
    Object objectKey0 = new Object();
    Object objectValue0 = new Object();
    Object objectKey1 = new Object();
    Object objectValue1 = new Object();
    Map<Object, Object> ofObject = Map.ofEntries(
        new AbstractMap.SimpleEntry<>(objectKey0, objectValue0),
        new AbstractMap.SimpleEntry<>(objectKey1, objectValue1));
    assertEquals(2, ofObject.size());
    assertSame(objectValue0, ofObject.get(objectKey0));
    assertSame(objectValue1, ofObject.get(objectKey1));
    assertEquals(null, ofObject.get(new Object()));
    assertMutationNotAllowed(ofObject);

    Map<Integer, Integer> ofInteger = Map.ofEntries(
        new AbstractMap.SimpleEntry<>(0, 0),
        new AbstractMap.SimpleEntry<>(1, 1));
    assertEquals(2, ofInteger.size());
    assertEquals(0, ofInteger.get(0));
    assertEquals(1, ofInteger.get(1));
    assertEquals(null, ofInteger.get(2));

    Map<Object, Object> ofMixed = Map.ofEntries(
        new AbstractMap.SimpleEntry<>(0, objectValue0),
        new AbstractMap.SimpleEntry<>(objectKey1, 1));
    assertEquals(2, ofMixed.size());
    assertSame(objectValue0, ofMixed.get(0));
    assertEquals(1, ofMixed.get(objectKey1));
    assertEquals(null, ofMixed.get(1));

    // Ensure the supplied entry objects are not used directly since they are mutable.
    Map.Entry<Object, Object> mutableEntry =
        new AbstractMap.SimpleEntry<>(objectKey0, objectValue0);
    Map<Object, Object> ofMutableEntry = Map.ofEntries(mutableEntry);
    mutableEntry.setValue(objectValue1);
    assertSame(objectValue0, ofMutableEntry.get(objectKey0));

    // Ensure the supplied mutable array is not used directly since it is mutable.
    @SuppressWarnings("unchecked")
    Map.Entry<Object, Object>[] mutableArray =
        new Map.Entry[] { new AbstractMap.SimpleEntry<>(objectKey0, objectValue0) };
    Map<Object, Object> ofArray = Map.ofEntries(mutableArray);
    mutableArray[0] = new AbstractMap.SimpleEntry<>(objectKey1, objectValue1);
    assertSame(objectValue0, ofArray.get(objectKey0));
    assertEquals(null, ofArray.get(objectKey1));

    try {
      Map.ofEntries(new AbstractMap.SimpleEntry<Object, Integer>(null, 1));
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map.ofEntries(new AbstractMap.SimpleEntry<Object, Integer>(1, null));
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }

    try {
      Map.ofEntries(
          new AbstractMap.SimpleEntry<>(0, objectValue0),
          new AbstractMap.SimpleEntry<>(0, objectValue1));
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertEquals("duplicate key: 0", expected.getMessage());
    }
  }

  private static void testEntry() {
    Object key = new Object();
    Object value = new Object();
    Map.Entry<Object, Object> entry = Map.entry(key, value);
    assertSame(key, entry.getKey());
    assertSame(value, entry.getValue());

    try {
      entry.setValue(new Object());
      throw new AssertionError();
    } catch (UnsupportedOperationException expected) {
    }

    try {
      Map.entry(null, value);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      Map.entry(key, null);
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
