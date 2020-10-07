// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class IterableUtils {

  public static <T> List<T> ensureUnmodifiableList(Iterable<T> iterable) {
    List<T> list;
    if (iterable instanceof List<?>) {
      list = (List<T>) iterable;
    } else {
      list = toNewArrayList(iterable);
    }
    return Collections.unmodifiableList(list);
  }

  public static <T> int firstIndexMatching(Iterable<T> iterable, Predicate<T> tester) {
    int i = 0;
    for (T element : iterable) {
      if (tester.test(element)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public static <T> Iterable<T> filter(Iterable<T> iterable, Predicate<T> predicate) {
    return () -> IteratorUtils.filter(iterable.iterator(), predicate);
  }

  public static <T> int size(Iterable<T> iterable) {
    int result = 0;
    for (T element : iterable) {
      result++;
    }
    return result;
  }

  public static <T> List<T> toNewArrayList(Iterable<T> iterable) {
    List<T> result = new ArrayList<>();
    iterable.forEach(result::add);
    return result;
  }

  public static <T> boolean isEmpty(Iterable<T> iterable) {
    return !iterable.iterator().hasNext();
  }
}
