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
}
