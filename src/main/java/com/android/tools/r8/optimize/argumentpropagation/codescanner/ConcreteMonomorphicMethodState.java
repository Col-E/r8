// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.google.common.collect.Iterables;
import java.util.List;

public class ConcreteMonomorphicMethodState extends ConcreteMethodState {

  List<ParameterState> parameterStates;

  public ConcreteMonomorphicMethodState(List<ParameterState> parameterStates) {
    assert Iterables.any(parameterStates, parameterState -> !parameterState.isUnknown())
        : "Must use UnknownMethodState instead";
    this.parameterStates = parameterStates;
  }
}
