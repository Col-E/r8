// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import java.util.function.Consumer;

public class LookupLambdaTarget implements LookupTarget {

  private final LambdaDescriptor lambda;
  private final DexClassAndMethod method;

  public LookupLambdaTarget(LambdaDescriptor lambda, DexClassAndMethod method) {
    assert lambda != null;
    assert method != null;
    this.lambda = lambda;
    this.method = method;
  }

  @Override
  public boolean isLambdaTarget() {
    return true;
  }

  @Override
  public LookupLambdaTarget asLambdaTarget() {
    return this;
  }

  @Override
  public LookupTarget toLookupTarget(DexClassAndMethod classAndMethod) {
    return new LookupLambdaTarget(lambda, classAndMethod);
  }

  @Override
  public void accept(
      Consumer<LookupMethodTarget> methodConsumer, Consumer<LookupLambdaTarget> lambdaConsumer) {
    lambdaConsumer.accept(this);
  }

  public DexClassAndMethod getImplementationMethod() {
    return method;
  }

  @Override
  public DexClassAndMethod getTargetOrImplementationMethod() {
    return getImplementationMethod();
  }
}
