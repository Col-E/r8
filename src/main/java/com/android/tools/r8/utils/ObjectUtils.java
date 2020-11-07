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

  public static <S, T> T mapNotNull(S object, Function<? super S, ? extends T> fn) {
    if (object != null) {
      return fn.apply(object);
    }
    return null;
  }
}
