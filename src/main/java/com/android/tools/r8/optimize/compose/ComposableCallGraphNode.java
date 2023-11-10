// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.SetUtils;
import java.util.Set;
import java.util.function.Consumer;

public class ComposableCallGraphNode {

  private final ProgramMethod method;
  private final boolean isComposable;

  private final Set<ComposableCallGraphNode> callers = SetUtils.newIdentityHashSet();
  private final Set<ComposableCallGraphNode> callees = SetUtils.newIdentityHashSet();

  ComposableCallGraphNode(ProgramMethod method, boolean isComposable) {
    this.method = method;
    this.isComposable = isComposable;
  }

  public void addCaller(ComposableCallGraphNode caller) {
    callers.add(caller);
    caller.callees.add(this);
  }

  public void forEachComposableCallee(Consumer<ComposableCallGraphNode> consumer) {
    for (ComposableCallGraphNode callee : callees) {
      if (callee.isComposable()) {
        consumer.accept(callee);
      }
    }
  }

  public Set<ComposableCallGraphNode> getCallers() {
    return callers;
  }

  public ProgramMethod getMethod() {
    return method;
  }

  public boolean isComposable() {
    return isComposable;
  }

  @Override
  public String toString() {
    return method.toString();
  }
}
