// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Predicate;

public class IterableUtils {

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

  public static <T> Iterable<T> filter(Iterable<T> methods, Predicate<T> predicate) {
    return () -> IteratorUtils.filter(methods.iterator(), predicate);
  }
}
