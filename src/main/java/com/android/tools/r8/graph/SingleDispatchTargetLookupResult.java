// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;

public class SingleDispatchTargetLookupResult extends DispatchTargetLookupResult {

  private final DexClassAndMethod singleDispatchTarget;

  public SingleDispatchTargetLookupResult(
      DexClassAndMethod singleDispatchTarget, SingleResolutionResult<?> singleResolutionResult) {
    super(singleResolutionResult);
    assert singleDispatchTarget != null;
    this.singleDispatchTarget = singleDispatchTarget;
  }

  @Override
  public DexClassAndMethod getSingleDispatchTarget() {
    return singleDispatchTarget;
  }

  @Override
  public boolean isSingleResult() {
    return true;
  }

  @Override
  public SingleDispatchTargetLookupResult asSingleResult() {
    return this;
  }
}
