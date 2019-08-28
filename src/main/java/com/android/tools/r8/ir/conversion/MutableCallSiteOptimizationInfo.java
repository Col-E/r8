// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;

public class MutableCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  public MutableCallSiteOptimizationInfo(DexEncodedMethod encodedMethod) {
  }

  @Override
  public Nullability getNullability(int argIndex) {
    return Nullability.maybeNull();
  }

  @Override
  public boolean isMutableCallSiteOptimizationInfo() {
    return true;
  }

  @Override
  public MutableCallSiteOptimizationInfo asMutableCallSiteOptimizationInfo() {
    return this;
  }
}
