// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;

public abstract class DispatchTargetLookupResult {

  private final SingleResolutionResult<?> singleResolutionResult;

  DispatchTargetLookupResult(SingleResolutionResult<?> singleResolutionResult) {
    assert singleResolutionResult != null;
    this.singleResolutionResult = singleResolutionResult;
  }

  public static DispatchTargetLookupResult create(
      DexClassAndMethod singleDispatchTarget, SingleResolutionResult<?> singleResolutionResult) {
    if (singleDispatchTarget != null) {
      return new SingleDispatchTargetLookupResult(singleDispatchTarget, singleResolutionResult);
    } else {
      return new UnknownDispatchTargetLookupResult(singleResolutionResult);
    }
  }

  public SingleResolutionResult<?> getResolutionResult() {
    return singleResolutionResult;
  }

  public DexClassAndMethod getSingleDispatchTarget() {
    return null;
  }

  public boolean isSingleResult() {
    return false;
  }

  public SingleDispatchTargetLookupResult asSingleResult() {
    return null;
  }
}
