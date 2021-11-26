// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;

// A flat lattice structure:
//   BOTTOM, TOP, and a lattice element that holds accumulated argument info.
public abstract class CallSiteOptimizationInfo {

  public static TopCallSiteOptimizationInfo top() {
    return TopCallSiteOptimizationInfo.getInstance();
  }

  public boolean isTop() {
    return false;
  }

  public boolean isConcreteCallSiteOptimizationInfo() {
    return false;
  }

  public ConcreteCallSiteOptimizationInfo asConcreteCallSiteOptimizationInfo() {
    return null;
  }

  public CallSiteOptimizationInfo join(
      CallSiteOptimizationInfo other, AppView<?> appView, DexEncodedMethod method) {
    if (other.isTop()) {
      return other;
    }
    if (isTop()) {
      return this;
    }
    assert isConcreteCallSiteOptimizationInfo() && other.isConcreteCallSiteOptimizationInfo();
    return asConcreteCallSiteOptimizationInfo()
        .join(other.asConcreteCallSiteOptimizationInfo(), appView, method);
  }

  public boolean hasUsefulOptimizationInfo(AppView<?> appView, DexEncodedMethod method) {
    return false;
  }


  // The index exactly matches with in values of invocation, i.e., even including receiver.
  public TypeElement getDynamicUpperBoundType(int argIndex) {
    return null;
  }

  // TODO(b/139246447): dynamic lower bound type?

  // TODO(b/69963623): we need to re-run unused argument removal?

  // The index exactly matches with in values of invocation, i.e., even including receiver.
  public AbstractValue getAbstractArgumentValue(int argIndex) {
    return UnknownValue.getInstance();
  }

  // TODO(b/139249918): propagate classes that are guaranteed to be initialized.
}
