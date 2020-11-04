// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class FunctionUtils {

  public static <S, T> T applyOrElse(S object, Function<S, T> fn, T orElse) {
    return object != null ? fn.apply(object) : orElse;
  }

  public static <S, T, R> Function<S, Function<T, R>> curry(BiFunction<S, T, R> function) {
    return arg -> arg2 -> function.apply(arg, arg2);
  }

  public static <S, T, R> Function<T, R> apply(BiFunction<S, T, R> function, S arg) {
    return curry(function).apply(arg);
  }

  public static <T, R> void forEachApply(
      Iterable<T> list, Function<T, Consumer<R>> func, R argument) {
    for (T t : list) {
      func.apply(t).accept(argument);
    }
  }
}
