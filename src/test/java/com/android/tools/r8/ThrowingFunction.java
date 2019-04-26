// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<Formal, Return, Exc extends Throwable> {
  Return apply(Formal formal) throws Exc;

  @SuppressWarnings("unchecked")
  default <T extends Throwable> ThrowingFunction<Formal, Return, T> withHandler(
      Function<Exc, T> handler) {
    return argument -> {
      try {
        return apply(argument);
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

  default Return applyWithRuntimeException(Formal argument) {
    return withHandler(RuntimeException::new).apply(argument);
  }
}
