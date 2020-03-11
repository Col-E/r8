// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class MethodCollectionBacking {

  abstract TraversalContinuation traverse(Function<DexEncodedMethod, TraversalContinuation> fn);

  void forEachMethod(Consumer<DexEncodedMethod> fn) {
    traverse(
        method -> {
          fn.accept(method);
          return TraversalContinuation.CONTINUE;
        });
  }
}
