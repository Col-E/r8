// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;

public abstract class NodeBase<N extends NodeBase<N>> {

  private final ProgramMethod method;

  public NodeBase(ProgramMethod method) {
    this.method = method;
  }

  public abstract void addCallerConcurrently(N caller, boolean likelySpuriousCallEdge);

  public abstract void addReaderConcurrently(N reader);

  public DexEncodedMethod getMethod() {
    return getProgramMethod().getDefinition();
  }

  public ProgramMethod getProgramMethod() {
    return method;
  }
}
