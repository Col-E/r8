// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MethodStateCollectionBySignature extends MethodStateCollection<DexMethodSignature> {

  private MethodStateCollectionBySignature(Map<DexMethodSignature, MethodState> methodStates) {
    super(methodStates);
  }

  public static MethodStateCollectionBySignature create() {
    return new MethodStateCollectionBySignature(new HashMap<>());
  }

  public static MethodStateCollectionBySignature createConcurrent() {
    return new MethodStateCollectionBySignature(new ConcurrentHashMap<>());
  }

  @Override
  DexMethodSignature getKey(ProgramMethod method) {
    return method.getMethodSignature();
  }

  @Override
  DexMethodSignature getSignature(DexMethodSignature method) {
    return method;
  }
}
