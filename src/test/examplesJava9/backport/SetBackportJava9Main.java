// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.Set;

public class SetBackportJava9Main {

  public static void main(String[] args) {
    testOf0();
    testOf1();
    testOf2();
    testOf10();
    testOfVarargs();
  }

  private static void testOf0() {
    Set<Object> ofObject = Set.of();
    assertEquals(0, ofObject.size());
    assertFalse(ofObject.contains(new Object()));
    assertMutationNotAllowed(ofObject);

    Set<Integer> ofInteger = Set.of();
    assertEquals(0, ofInteger.size());
    assertFalse(ofInteger.contains(0));
  }

  private static void testOf1() {
    Object anObject = new Object();
    Set<Object> ofObject = Set.of(anObject);
    assertEquals(1, ofObject.size());
    assertTrue(ofObject.contains(anObject));
    assertFalse(ofObject.contains(new Object()));
    assertMutationNotAllowed(ofObject);

    Set<Integer> ofInteger = Set.of(1);
    assertEquals(1, ofInteger.size());
    assertTrue(ofInteger.contains(1));
    assertFalse(ofInteger.contains(2));

    try {
      Set.of((Object) null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void testOf2() {
    Object anObject0 = new Object();
    Object anObject1 = new Object();
    Set<Object> ofObject = Set.of(anObject0, anObject1);
    assertEquals(2, ofObject.size());
    assertTrue(ofObject.contains(anObject0));
    assertTrue(ofObject.contains(anObject1));
    assertFalse(ofObject.contains(new Object()));
    assertMutationNotAllowed(ofObject);

    Set<Integer> ofInteger = Set.of(1, 2);
    assertEquals(2, ofInteger.size());
    assertTrue(ofInteger.contains(1));
    assertTrue(ofInteger.contains(2));
    assertFalse(ofInteger.contains(3));

    Set<Object> ofMixed = Set.of(anObject0, 1);
    assertEquals(2, ofMixed.size());
    assertTrue(ofMixed.contains(anObject0));
    assertTrue(ofMixed.contains(1));
    assertFalse(ofMixed.contains(2));
    assertFalse(ofMixed.contains(anObject1));
    assertMutationNotAllowed(ofMixed);

    try {
      Set.of(1, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }

    try {
      Set.of(1, 1);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertEquals("duplicate element: 1", expected.getMessage());
    }
  }

  private static void testOf10() {
    Object anObject0 = new Object();
    Object anObject6 = new Object();
    Object anObject9 = new Object();
    Set<Object> ofObject =
        Set.of(anObject0, new Object(), new Object(), new Object(), new Object(), new Object(),
            anObject6, new Object(), new Object(), anObject9);
    assertEquals(10, ofObject.size());
    assertTrue(ofObject.contains(anObject0));
    assertTrue(ofObject.contains(anObject6));
    assertTrue(ofObject.contains(anObject9));
    assertFalse(ofObject.contains(new Object()));
    assertMutationNotAllowed(ofObject);

    Set<Integer> ofInteger = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    assertEquals(10, ofInteger.size());
    assertTrue(ofInteger.contains(0));
    assertTrue(ofInteger.contains(6));
    assertTrue(ofInteger.contains(9));
    assertFalse(ofInteger.contains(10));

    Set<Object> ofMixed = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, anObject9);
    assertEquals(10, ofMixed.size());
    assertTrue(ofMixed.contains(0));
    assertTrue(ofMixed.contains(6));
    assertTrue(ofMixed.contains(anObject9));
    assertFalse(ofMixed.contains(anObject0));
    assertMutationNotAllowed(ofMixed);

    try {
      Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }

    try {
      Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 0);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertEquals("duplicate element: 0", expected.getMessage());
    }
  }

  private static void testOfVarargs() {
    Object anObject0 = new Object();
    Object anObject6 = new Object();
    Object anObject10 = new Object();
    Set<Object> ofObject =
        Set.of(anObject0, new Object(), new Object(), new Object(), new Object(), new Object(),
            anObject6, new Object(), new Object(), new Object(), anObject10);
    assertEquals(11, ofObject.size());
    assertTrue(ofObject.contains(anObject0));
    assertTrue(ofObject.contains(anObject6));
    assertTrue(ofObject.contains(anObject10));
    assertFalse(ofObject.contains(new Object()));
    assertMutationNotAllowed(ofObject);

    Set<Integer> ofInteger = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    assertEquals(11, ofInteger.size());
    assertTrue(ofInteger.contains(0));
    assertTrue(ofInteger.contains(6));
    assertTrue(ofInteger.contains(10));
    assertFalse(ofInteger.contains(11));

    Set<Object> ofMixed = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, anObject10);
    assertEquals(11, ofMixed.size());
    assertTrue(ofMixed.contains(0));
    assertTrue(ofMixed.contains(6));
    assertTrue(ofMixed.contains(anObject10));
    assertFalse(ofMixed.contains(10));
    assertFalse(ofMixed.contains(anObject0));
    assertMutationNotAllowed(ofMixed);

    // Ensure the supplied mutable array is not used directly since it is mutable.
    Object[] mutableArray = { anObject0 };
    Set<Object> ofMutableArray = Set.of(mutableArray);
    mutableArray[0] = anObject10;
    assertTrue(ofMutableArray.contains(anObject0));
    assertFalse(ofMutableArray.contains(anObject10));

    try {
      Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }

    try {
      Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertEquals("duplicate element: 0", expected.getMessage());
    }
  }

  private static void assertMutationNotAllowed(Set<Object> ofObject) {
    try {
      ofObject.add(new Object());
      throw new AssertionError();
    } catch (UnsupportedOperationException expected) {
    }
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

  private static void assertEquals(Object expected, Object actual) {
    if (expected != actual && !expected.equals(actual)) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
    }
  }
}
