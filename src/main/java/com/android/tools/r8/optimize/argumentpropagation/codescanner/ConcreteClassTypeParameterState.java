// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class ConcreteClassTypeParameterState extends ConcreteParameterState {

  private final AbstractValue abstractValue;
  private final DynamicType dynamicType;

  public ConcreteClassTypeParameterState(MethodParameter inParameter) {
    super(inParameter);
    this.abstractValue = AbstractValue.bottom();
    this.dynamicType = DynamicType.bottom();
  }

  public ConcreteClassTypeParameterState(AbstractValue abstractValue, DynamicType dynamicType) {
    this.abstractValue = abstractValue;
    this.dynamicType = dynamicType;
  }
}
