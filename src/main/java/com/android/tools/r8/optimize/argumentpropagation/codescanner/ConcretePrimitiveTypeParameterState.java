// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class ConcretePrimitiveTypeParameterState extends ConcreteParameterState {

  private final AbstractValue abstractValue;

  public ConcretePrimitiveTypeParameterState(AbstractValue abstractValue) {
    assert !abstractValue.isUnknown() : "Must use UnknownParameterState";
    this.abstractValue = abstractValue;
  }

  public ConcretePrimitiveTypeParameterState(MethodParameter inParameter) {
    super(inParameter);
    this.abstractValue = AbstractValue.bottom();
  }
}
