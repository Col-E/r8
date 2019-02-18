// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.function.Function;

public interface ThrowableFunction<Formal, Return> {
  Return apply(Formal formal) throws Throwable;

  default <T extends Throwable> Return applyWithHandler(
      Formal formal, Function<Throwable, T> handler) throws T {
    try {
      return apply(formal);
    } catch (Throwable e) {
      throw handler.apply(e);
    }
  }

  default Return applyWithRuntimeException(Formal formal) {
    return applyWithHandler(formal, RuntimeException::new);
  }
}
