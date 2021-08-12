// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class MethodStateCollection {

  private final Map<DexMethod, MethodState> methodStates;

  private MethodStateCollection(Map<DexMethod, MethodState> methodStates) {
    this.methodStates = methodStates;
  }

  public static MethodStateCollection create() {
    return new MethodStateCollection(new IdentityHashMap<>());
  }

  public static MethodStateCollection createConcurrent() {
    return new MethodStateCollection(new ConcurrentHashMap<>());
  }

  /**
   * This intentionally takes a {@link Supplier<MethodState>} to avoid computing the method state
   * for a given call site when nothing is known about the arguments of the method.
   */
  public void addMethodState(
      AppView<AppInfoWithLiveness> appView,
      DexMethod method,
      Supplier<MethodState> methodStateSupplier) {
    methodStates.compute(
        method,
        (ignore, existingMethodState) -> {
          if (existingMethodState == null) {
            return methodStateSupplier.get();
          }
          return existingMethodState.mutableJoin(appView, methodStateSupplier);
        });
  }

  public void addMethodStates(AppView<AppInfoWithLiveness> appView, MethodStateCollection other) {
    other.methodStates.forEach(
        (method, methodState) -> addMethodState(appView, method, () -> methodState));
  }

  public void forEach(BiConsumer<DexMethod, MethodState> consumer) {
    methodStates.forEach(consumer);
  }

  public MethodState get(ProgramMethod method) {
    return methodStates.get(method.getReference());
  }
}
