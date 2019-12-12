// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.Arrays;
import java.util.List;

public class ListBackportJava10Main {

  public static void main(String[] args) {
    testCopyOf();
  }

  private static void testCopyOf() {
    Object anObject0 = new Object();
    Object anObject1 = new Object();
    List<Object> original = Arrays.asList(anObject0, anObject1);
    List<Object> copy = List.copyOf(original);
    assertEquals(2, copy.size());
    assertEquals(original, copy);
    assertSame(anObject0, copy.get(0));
    assertSame(anObject1, copy.get(1));
    assertMutationNotAllowed(copy);

    // Mutate the original backing collection and ensure it's not reflected in copy.
    original.set(0, new Object());
    assertSame(anObject0, copy.get(0));

    try {
      List.copyOf(null);
      throw new AssertionError();
    } catch (NullPointerException expected) {
    }
    try {
      List.copyOf(Arrays.asList(1, null, 2));
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
