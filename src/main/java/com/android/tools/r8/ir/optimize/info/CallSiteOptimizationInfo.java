// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.CallSiteOptimizationInfoPropagator;

public abstract class CallSiteOptimizationInfo {

  public boolean isDefaultCallSiteOptimizationInfo() {
    return false;
  }

  public DefaultCallSiteOptimizationInfo asDefaultCallSiteOptimizationInfo() {
    return null;
  }

  public boolean isMutableCallSiteOptimizationInfo() {
    return false;
  }

  public MutableCallSiteOptimizationInfo asMutableCallSiteOptimizationInfo() {
    return null;
  }

  /**
   * {@link CallSiteOptimizationInfoPropagator} will reprocess the call target if its collected call
   * site optimization info has something useful that can trigger more optimizations. For example,
   * if a certain argument is guaranteed to be definitely not null for all call sites, null-check on
   * that argument can be simplified during the reprocessing of the method.
   */
  public boolean hasUsefulOptimizationInfo(AppView<?> appView, DexEncodedMethod encodedMethod) {
    return false;
  }

  // The index exactly matches with in values of invocation, i.e., even including receiver.
  public abstract TypeLatticeElement getDynamicUpperBoundType(int argIndex);

  // TODO(b/139246447): dynamic lower bound type?

  // TODO(b/69963623): collect constants and if they're all same, propagate it to the callee.
  //   then, we need to re-run unused argument removal?

  // TODO(b/139249918): propagate classes that are guaranteed to be initialized.
}
