// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.ir.analysis.type.DynamicType;

public class ConcreteReceiverParameterState extends ConcreteParameterState {

  private final DynamicType type;

  public ConcreteReceiverParameterState(DynamicType type) {
    this.type = type;
  }
}
