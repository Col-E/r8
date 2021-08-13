// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MethodStateCollectionByReference extends MethodStateCollection<DexMethod> {

  private MethodStateCollectionByReference(Map<DexMethod, MethodState> methodStates) {
    super(methodStates);
  }

  public static MethodStateCollectionByReference create() {
    return new MethodStateCollectionByReference(new IdentityHashMap<>());
  }

  public static MethodStateCollectionByReference createConcurrent() {
    return new MethodStateCollectionByReference(new ConcurrentHashMap<>());
  }

  @Override
  DexMethod getKey(ProgramMethod method) {
    return method.getReference();
  }
}
