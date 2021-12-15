// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.multicallerinliner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.NodeBase;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiCallerInlinerNode extends NodeBase<MultiCallerInlinerNode> {

  private final AtomicInteger numberOfCallSites = new AtomicInteger();

  public MultiCallerInlinerNode(ProgramMethod method) {
    super(method);
  }

  @Override
  public void addCallerConcurrently(MultiCallerInlinerNode caller, boolean likelySpuriousCallEdge) {
    assert !getMethod().isClassInitializer();
    numberOfCallSites.incrementAndGet();
  }

  @Override
  public void addReaderConcurrently(MultiCallerInlinerNode reader) {
    throw new Unreachable();
  }

  public int getNumberOfCallSites() {
    return numberOfCallSites.get();
  }
}
