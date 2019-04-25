// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.function.Function;

public interface ThrowableBiFunction<Formal1, Formal2, Return> {
  Return apply(Formal1 formal1, Formal2 formal2) throws Throwable;

  default <T extends Throwable> Return applyWithHandler(
      Formal1 formal1, Formal2 formal2, Function<Throwable, T> handler) throws T {
    try {
      return apply(formal1, formal2);
    } catch (Throwable e) {
      throw handler.apply(e);
    }
  }

  default Return applyWithRuntimeException(Formal1 formal1, Formal2 formal2) {
    return applyWithHandler(formal1, formal2, RuntimeException::new);
  }
}
