// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.function.Consumer;

public interface LookupTarget {
  default boolean isMethodTarget() {
    return false;
  }

  default boolean isLambdaTarget() {
    return false;
  }

  default LookupMethodTarget asMethodTarget() {
    return null;
  }

  default LookupLambdaTarget asLambdaTarget() {
    return null;
  }

  default DexClassAndMethod getAccessOverride() {
    return null;
  }

  LookupTarget toLookupTarget(DexClassAndMethod classAndMethod);

  void accept(
      Consumer<LookupMethodTarget> methodConsumer, Consumer<LookupLambdaTarget> lambdaConsumer);

  DexClassAndMethod getTargetOrImplementationMethod();
}
