// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.BiPredicate;

public class BiPredicateUtils {

  public static <S, T> BiPredicate<S, T> alwaysFalse() {
    return (s, t) -> false;
  }

  public static <S, T> BiPredicate<S, T> alwaysTrue() {
    return (s, t) -> true;
  }

  @SafeVarargs
  public static <S, T> BiPredicate<S, T> or(BiPredicate<S, T>... predicates) {
    return (s, t) -> {
      for (BiPredicate<S, T> predicate : predicates) {
        if (predicate.test(s, t)) {
          return true;
        }
      }
      return false;
    };
  }
}
