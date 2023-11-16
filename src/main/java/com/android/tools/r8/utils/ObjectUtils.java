// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Function;
import java.util.function.Predicate;

public class ObjectUtils {

  public static <T> boolean getBooleanOrElse(T object, Predicate<T> fn, boolean orElse) {
    if (object != null) {
      return fn.test(object);
    }
    return orElse;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(Object a, Object b) {
    return a == b;
  }

  public static boolean notIdentical(Object a, Object b) {
    return !identical(a, b);
  }

  public static <S, T> T mapNotNull(S object, Function<? super S, ? extends T> fn) {
    if (object != null) {
      return fn.apply(object);
    }
    return null;
  }

  /**
   * If the object is null return the default value, otherwise compute the function with the value.
   */
  public static <S, T> T mapNotNullOrDefault(S object, T def, Function<? super S, ? extends T> fn) {
    if (object != null) {
      return fn.apply(object);
    }
    return def;
  }
}
