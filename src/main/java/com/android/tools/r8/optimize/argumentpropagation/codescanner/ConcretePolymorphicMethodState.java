// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.ir.analysis.type.DynamicType;

public class ConcretePolymorphicMethodState extends ConcreteMethodState {

  public ConcretePolymorphicMethodState() {}

  public void setStateForReceiverBounds(DynamicType bounds, MethodState state) {
    // TODO: If we have an unknown state for some bounds, then consider widening the entire state to
    //  be unknown (add a strategy to allow easily experimenting with this).
    throw new Unimplemented();
  }
}
