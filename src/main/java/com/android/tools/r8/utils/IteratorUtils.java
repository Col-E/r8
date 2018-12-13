// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Predicate;

public class IteratorUtils {
  public static <T> T peekPrevious(ListIterator<T> iterator) {
    T previous = iterator.previous();
    T next = iterator.next();
    assert previous == next;
    return previous;
  }

  public static <T> T peekNext(ListIterator<T> iterator) {
    if (iterator.hasNext()) {
      T next = iterator.next();
      T previous = iterator.previous();
      assert previous == next;
      return next;
    }
    return null;
  }

  public static <T> void removeIf(Iterator<T> iterator, Predicate<T> predicate) {
    while (iterator.hasNext()) {
      T item = iterator.next();
      if (predicate.test(item)) {
        iterator.remove();
      }
    }
  }

  public static <T> boolean allRemainingMatch(ListIterator<T> iterator, Predicate<T> predicate) {
    return !anyRemainingMatch(iterator, remaining -> !predicate.test(remaining));
  }

  public static <T> boolean anyRemainingMatch(ListIterator<T> iterator, Predicate<T> predicate) {
    T state = peekNext(iterator);
    boolean result = false;
    while (iterator.hasNext()) {
      T item = iterator.next();
      if (predicate.test(item)) {
        result = true;
        break;
      }
    }
    while (iterator.hasPrevious() && iterator.previous() != state) {
      // Restore the state of the iterator.
    }
    assert peekNext(iterator) == state;
    return result;
  }
}
