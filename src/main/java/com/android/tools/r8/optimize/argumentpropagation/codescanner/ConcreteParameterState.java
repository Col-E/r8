// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;

public abstract class ConcreteParameterState extends ParameterState {

  private final Collection<MethodParameter> inParameters;

  ConcreteParameterState() {
    this.inParameters = Collections.emptyList();
  }

  ConcreteParameterState(MethodParameter inParameter) {
    this.inParameters = ImmutableList.of(inParameter);
  }
}
