// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Collection;
import java.util.Map;

public abstract class CallGraphBase<N extends NodeBase<N>> {

  final Map<DexMethod, N> nodes;

  public CallGraphBase(Map<DexMethod, N> nodes) {
    this.nodes = nodes;
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  public N getNode(ProgramMethod method) {
    return nodes.get(method.getReference());
  }

  public Collection<N> getNodes() {
    return nodes.values();
  }
}
