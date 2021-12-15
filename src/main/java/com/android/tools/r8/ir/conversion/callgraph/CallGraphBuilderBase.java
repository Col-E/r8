// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class CallGraphBuilderBase<N extends NodeBase<N>> {

  protected final AppView<AppInfoWithLiveness> appView;

  protected final Map<DexMethod, N> nodes = new ConcurrentHashMap<>();
  protected final Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache =
      new ConcurrentHashMap<>();

  public CallGraphBuilderBase(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  protected abstract N createNode(ProgramMethod method);

  protected N getOrCreateNode(ProgramMethod method) {
    return nodes.computeIfAbsent(method.getReference(), ignore -> createNode(method));
  }
}
