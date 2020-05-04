// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

/**
 * Similar to a {@link java.util.function.BiFunction} but throws a single {@link Throwable}.
 *
 * @param <S> the type of the first input
 * @param <T> the type of the second input
 * @param <E> the type of the {@link Throwable}
 */
@FunctionalInterface
public interface ThrowingBiFunction<S, T, R, E extends Throwable> {
  R apply(S s, T t) throws E;
}
