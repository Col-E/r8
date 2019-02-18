// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.function.Function;

public interface ThrowableConsumer<Formal> {
  void accept(Formal formal) throws Throwable;

  default <T extends Throwable> void acceptWithHandler(
      Formal formal, Function<Throwable, T> handler) throws T {
    try {
      accept(formal);
    } catch (Throwable e) {
      throw handler.apply(e);
    }
  }

  default void acceptWithRuntimeException(Formal formal) {
    acceptWithHandler(formal, RuntimeException::new);
  }
}
