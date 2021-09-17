// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.ProgramMethod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MethodParameterFactory {

  private final Map<MethodParameter, MethodParameter> methodParameters = new ConcurrentHashMap<>();

  public MethodParameter create(ProgramMethod method, int argumentIndex) {
    return methodParameters.computeIfAbsent(
        new MethodParameter(method.getReference(), argumentIndex), Function.identity());
  }
}
