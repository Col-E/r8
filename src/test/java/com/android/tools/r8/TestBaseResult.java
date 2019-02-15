// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class TestBaseResult<CR extends TestBaseResult<CR, RR>, RR extends TestRunResult> {
  final TestState state;

  TestBaseResult(TestState state) {
    this.state = state;
  }

  public abstract CR self();

  public <S> S map(Function<CR, S> fn) {
    return fn.apply(self());
  }

  public CR apply(Consumer<CR> fn) {
    fn.accept(self());
    return self();
  }
}
