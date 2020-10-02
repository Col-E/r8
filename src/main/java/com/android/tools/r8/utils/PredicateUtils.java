// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Function;
import java.util.function.Predicate;

public class PredicateUtils {

  public static <T> T findFirst(T[] items, Predicate<T> predicate) {
    for (T entry : items) {
      if (predicate.test(entry)) {
        return entry;
      }
    }
    return null;
  }

  public static <T> Predicate<T> not(Predicate<T> predicate) {
    return t -> !predicate.test(t);
  }

  public static <T, R> Predicate<T> isNull(Function<T, R> func) {
    return t -> func.apply(t) == null;
  }
}
