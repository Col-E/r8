// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;

// A flat lattice structure: TOP and a lattice element that holds accumulated argument info.
public abstract class CallSiteOptimizationInfo {

  public static TopCallSiteOptimizationInfo top() {
    return TopCallSiteOptimizationInfo.getInstance();
  }

  public boolean isConcreteCallSiteOptimizationInfo() {
    return false;
  }

  public ConcreteCallSiteOptimizationInfo asConcreteCallSiteOptimizationInfo() {
    return null;
  }

  // The index exactly matches with in values of invocation, i.e., even including receiver.
  public DynamicType getDynamicType(int argIndex) {
    return DynamicType.unknown();
  }

  // The index exactly matches with in values of invocation, i.e., even including receiver.
  public AbstractValue getAbstractArgumentValue(int argIndex) {
    return UnknownValue.getInstance();
  }

  // TODO(b/139249918): propagate classes that are guaranteed to be initialized.
}
