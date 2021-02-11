// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.graph.DexMethod;

public interface ForcefullyMovedLambdaMethodConsumer {

  void acceptForcefullyMovedLambdaMethod(DexMethod from, DexMethod to);

  static ForcefullyMovedLambdaMethodConsumer emptyForcefullyMovedLambdaMethodConsumer() {
    return (from, to) -> {};
  }
}
