// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CollectionsBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public CollectionsBackportTest(TestParameters parameters) {
    super(parameters, Collections.class, Main.class);
    registerTarget(AndroidApiLevel.K, 3);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testEmptyEnumeration();
      testEmptyIterator();
      testEmptyListIterator();
    }

    private static void testEmptyEnumeration() {
      Enumeration<Object> enumeration = Collections.emptyEnumeration();
      assertEquals(false, enumeration.hasMoreElements());

      try {
        throw new AssertionError(enumeration.nextElement());
      } catch (NoSuchElementException expected) {
      }
    }

    private static void testEmptyIterator() {
      Iterator<Object> iterator = Collections.emptyIterator();
      assertEquals(false, iterator.hasNext());

      try {
        throw new AssertionError(iterator.next());
      } catch (NoSuchElementException expected) {
      }
      try {
        iterator.remove();
        throw new AssertionError();
      } catch (IllegalStateException expected) {
      }
    }

    private static void testEmptyListIterator() {
      ListIterator<Object> listIterator = Collections.emptyListIterator();
      assertEquals(false, listIterator.hasNext());
      assertEquals(0, listIterator.nextIndex());
      assertEquals(false, listIterator.hasPrevious());
      assertEquals(-1, listIterator.previousIndex());

      try {
        throw new AssertionError(listIterator.next());
      } catch (NoSuchElementException expected) {
      }
      try {
        throw new AssertionError(listIterator.previous());
      } catch (NoSuchElementException expected) {
      }
      try {
        listIterator.remove();
        throw new AssertionError();
      } catch (IllegalStateException expected) {
      }
      try {
        listIterator.add(new Object());
        throw new AssertionError();
      } catch (UnsupportedOperationException expected) {
      }
      try {
        listIterator.set(new Object());
        throw new AssertionError();
      } catch (IllegalStateException | UnsupportedOperationException expected) {
        // Reference implementation throws ISE, backport throws UOE. Both are spec-compliant.
      }
    }
  }
}
