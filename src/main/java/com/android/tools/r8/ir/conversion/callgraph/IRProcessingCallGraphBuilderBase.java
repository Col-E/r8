// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

abstract class IRProcessingCallGraphBuilderBase extends CallGraphBuilderBase<Node> {

  IRProcessingCallGraphBuilderBase(AppView<AppInfoWithLiveness> appView) {
    super(appView);
  }

  public CallGraph build(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Build IR processing order constraints");
    timing.begin("Build call graph");
    populateGraph(executorService);
    assert verifyNoRedundantFieldReadEdges();
    timing.end();
    assert verifyAllMethodsWithCodeExists();

    appView.withGeneratedMessageLiteBuilderShrinker(
        shrinker -> shrinker.preprocessCallGraphBeforeCycleElimination(nodes));

    timing.begin("Cycle elimination");
    // Sort the nodes for deterministic cycle elimination.
    Set<Node> nodesWithDeterministicOrder = Sets.newTreeSet(nodes.values());
    CycleEliminator cycleEliminator = new CycleEliminator();
    cycleEliminator.breakCycles(nodesWithDeterministicOrder);
    timing.end();
    timing.end();
    assert cycleEliminator.breakCycles(nodesWithDeterministicOrder).numberOfRemovedCallEdges()
        == 0; // The cycles should be gone.

    return new CallGraph(nodes);
  }

  @Override
  protected Node createNode(ProgramMethod method) {
    return new Node(method);
  }

  abstract void populateGraph(ExecutorService executorService) throws ExecutionException;

  /** Verify that there are no field read edges in the graph if there is also a call graph edge. */
  private boolean verifyNoRedundantFieldReadEdges() {
    for (Node writer : nodes.values()) {
      for (Node reader : writer.getReadersWithDeterministicOrder()) {
        assert !writer.hasCaller(reader);
      }
    }
    return true;
  }

  abstract boolean verifyAllMethodsWithCodeExists();
}
