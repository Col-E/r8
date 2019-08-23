// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.List;

public class ListBackportJava9Main {

  public static void main(String[] args) {
    testOf0();
    testOf1();
    testOf2();
    testOf10();
    testOfVarargs();
  }

  private static void testOf0() {
    List<Object> ofObject = List.of();
    assertEquals(0, ofObject.size());
    assertMutationNotAllowed(ofObject);

    List<Integer> ofInteger = List.of();
    assertEquals(0, ofInteger.size());
    assertMutationNotAllowed(ofObject);
  }

  private static void testOf1() {
    Object anObject = new Object();
    List<Object> ofObject = List.of(anObject);
    assertEquals(1, ofObject.size());
    assertSame(anObject, ofObject.get(0));
    assertMutationNotAllowed(ofObject);

    List<Integer> ofInteger = List.of(1);
    assertEquals(1, ofInteger.size());
    assertEquals(1, ofInteger.get(0));

    try {
      List.of((Object) null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void testOf2() {
    Object anObject0 = new Object();
    Object anObject1 = new Object();
    List<Object> ofObject = List.of(anObject0, anObject1);
    assertEquals(2, ofObject.size());
    assertSame(anObject0, ofObject.get(0));
    assertSame(anObject1, ofObject.get(1));
    assertMutationNotAllowed(ofObject);

    List<Integer> ofInteger = List.of(1, 2);
    assertEquals(2, ofInteger.size());
    assertEquals(1, ofInteger.get(0));
    assertEquals(2, ofInteger.get(1));

    List<Object> ofMixed = List.of(anObject0, 1);
    assertEquals(2, ofMixed.size());
    assertSame(anObject0, ofMixed.get(0));
    assertEquals(1, ofMixed.get(1));
    assertMutationNotAllowed(ofMixed);

    try {
      List.of(1, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void testOf10() {
    Object anObject0 = new Object();
    Object anObject6 = new Object();
    Object anObject9 = new Object();
    List<Object> ofObject =
        List.of(anObject0, new Object(), new Object(), new Object(), new Object(), new Object(),
            anObject6, new Object(), new Object(), anObject9);
    assertEquals(10, ofObject.size());
    assertSame(anObject0, ofObject.get(0));
    assertSame(anObject6, ofObject.get(6));
    assertSame(anObject9, ofObject.get(9));
    assertMutationNotAllowed(ofObject);

    List<Integer> ofInteger = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    assertEquals(10, ofInteger.size());
    assertEquals(0, ofInteger.get(0));
    assertEquals(6, ofInteger.get(6));
    assertEquals(9, ofInteger.get(9));

    List<Object> ofMixed = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, anObject9);
    assertEquals(10, ofMixed.size());
    assertEquals(0, ofMixed.get(0));
    assertEquals(6, ofMixed.get(6));
    assertSame(anObject9, ofMixed.get(9));
    assertMutationNotAllowed(ofMixed);

    try {
      List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void testOfVarargs() {
    Object anObject0 = new Object();
    Object anObject6 = new Object();
    Object anObject10 = new Object();
    List<Object> ofObject =
        List.of(anObject0, new Object(), new Object(), new Object(), new Object(), new Object(),
            anObject6, new Object(), new Object(), new Object(), anObject10);
    assertEquals(11, ofObject.size());
    assertSame(anObject0, ofObject.get(0));
    assertSame(anObject6, ofObject.get(6));
    assertSame(anObject10, ofObject.get(10));
    assertMutationNotAllowed(ofObject);

    List<Integer> ofInteger = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    assertEquals(11, ofInteger.size());
    assertEquals(0, ofInteger.get(0));
    assertEquals(6, ofInteger.get(6));
    assertEquals(10, ofInteger.get(10));

    List<Object> ofMixed = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, anObject10);
    assertEquals(11, ofMixed.size());
    assertEquals(0, ofMixed.get(0));
    assertEquals(6, ofMixed.get(6));
    assertSame(anObject10, ofMixed.get(10));
    assertMutationNotAllowed(ofMixed);

    // Ensure the supplied mutable array is not used directly since it is mutable.
    Object[] mutableArray = { anObject0 };
    List<Object> ofMutableArray = List.of(mutableArray);
    mutableArray[0] = anObject10;
    assertSame(anObject0, ofMutableArray.get(0));

    try {
      List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
  }

  private static void assertMutationNotAllowed(List<Object> ofObject) {
    try {
      ofObject.add(new Object());
      throw new AssertionError();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      ofObject.set(0, new Object());
      throw new AssertionError();
    } catch (UnsupportedOperationException expected) {
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
