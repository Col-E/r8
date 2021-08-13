// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

abstract class MethodStateCollection<K> {

  private final Map<K, MethodState> methodStates;

  MethodStateCollection(Map<K, MethodState> methodStates) {
    this.methodStates = methodStates;
  }

  abstract K getKey(ProgramMethod method);

  public void addMethodState(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, MethodState methodState) {
    addMethodState(appView, getKey(method), methodState);
  }

  private void addMethodState(
      AppView<AppInfoWithLiveness> appView, K method, MethodState methodState) {
    if (methodState.isUnknown()) {
      methodStates.put(method, methodState);
    } else {
      methodStates.compute(
          method,
          (ignore, existingMethodState) -> {
            if (existingMethodState == null) {
              return methodState;
            }
            return existingMethodState.mutableJoin(appView, methodState);
          });
    }
  }

  /**
   * This intentionally takes a {@link Supplier<MethodState>} to avoid computing the method state
   * for a given call site when nothing is known about the arguments of the method.
   */
  public void addMethodState(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      Supplier<MethodState> methodStateSupplier) {
    addMethodState(appView, getKey(method), methodStateSupplier);
  }

  public void addMethodState(
      AppView<AppInfoWithLiveness> appView, K method, Supplier<MethodState> methodStateSupplier) {
    methodStates.compute(
        method,
        (ignore, existingMethodState) -> {
          if (existingMethodState == null) {
            return methodStateSupplier.get();
          }
          return existingMethodState.mutableJoin(appView, methodStateSupplier);
        });
  }

  public void addMethodStates(
      AppView<AppInfoWithLiveness> appView, MethodStateCollection<K> other) {
    other.methodStates.forEach(
        (method, methodState) -> addMethodState(appView, method, methodState));
  }

  public void forEach(BiConsumer<K, MethodState> consumer) {
    methodStates.forEach(consumer);
  }

  public MethodState get(ProgramMethod method) {
    return methodStates.getOrDefault(getKey(method), MethodState.bottom());
  }

  public boolean isEmpty() {
    return methodStates.isEmpty();
  }

  public MethodState remove(ProgramMethod method) {
    MethodState removed = methodStates.remove(getKey(method));
    return removed != null ? removed : MethodState.bottom();
  }

  public void set(ProgramMethod method, MethodState methodState) {
    methodStates.put(getKey(method), methodState);
  }
}
