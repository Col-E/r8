// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.function.Consumer;

public interface LookupMethodTarget extends LookupTarget {

  @Override
  default boolean isMethodTarget() {
    return true;
  }

  @Override
  default LookupMethodTarget asMethodTarget() {
    return this;
  }

  @Override
  default void accept(
      Consumer<LookupMethodTarget> methodConsumer, Consumer<LookupLambdaTarget> lambdaConsumer) {
    methodConsumer.accept(this);
  }

  DexClass getHolder();

  DexMethod getReference();

  DexEncodedMethod getDefinition();

  DexClassAndMethod getTarget();

  @Override
  default DexClassAndMethod getTargetOrImplementationMethod() {
    return getTarget();
  }
}
