// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingBiFunction<Formal1, Formal2, Return, Exc extends Throwable> {
  Return apply(Formal1 formal1, Formal2 formal2) throws Exc;

  @SuppressWarnings("unchecked")
  default <T extends Throwable> ThrowingBiFunction<Formal1, Formal2, Return, T> withHandler(
      Function<Exc, T> handler) {
    return (argument1, argument2) -> {
      try {
        return apply(argument1, argument2);
      } catch (Throwable e) {
        Exc expected;
        try {
          expected = (Exc) e;
        } catch (ClassCastException failedCast) {
          // A failed cast must be on an unchecked exception for the types to be sound.
          throw (RuntimeException) e;
        }
        throw handler.apply(expected);
      }
    };
  }

  default Return applyWithRuntimeException(Formal1 formal1, Formal2 formal2) {
    return withHandler(RuntimeException::new).apply(formal1, formal2);
  }
}
