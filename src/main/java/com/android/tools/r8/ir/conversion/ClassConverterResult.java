// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.lambda.ForcefullyMovedLambdaMethodConsumer;
import java.util.IdentityHashMap;
import java.util.Map;

public class ClassConverterResult {

  private final Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods;

  private ClassConverterResult(Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods) {
    this.forcefullyMovedLambdaMethods = forcefullyMovedLambdaMethods;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Map<DexMethod, DexMethod> getForcefullyMovedLambdaMethods() {
    return forcefullyMovedLambdaMethods;
  }

  public static class Builder implements ForcefullyMovedLambdaMethodConsumer {

    private final Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods = new IdentityHashMap<>();

    @Override
    public void acceptForcefullyMovedLambdaMethod(DexMethod from, DexMethod to) {
      forcefullyMovedLambdaMethods.put(from, to);
    }

    public ClassConverterResult build() {
      return new ClassConverterResult(forcefullyMovedLambdaMethods);
    }
  }
}
