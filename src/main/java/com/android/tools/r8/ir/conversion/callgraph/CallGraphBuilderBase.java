// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CycleEliminator.CycleEliminationResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

abstract class CallGraphBuilderBase {

  final AppView<AppInfoWithLiveness> appView;
  final FieldAccessInfoCollection<?> fieldAccessInfoCollection;
  final Map<DexMethod, Node> nodes = new IdentityHashMap<>();
  final Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache = new ConcurrentHashMap<>();

  GraphLens codeLens;
  boolean includeFieldReadWriteEdges = true;

  CallGraphBuilderBase(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.fieldAccessInfoCollection = appView.appInfo().getFieldAccessInfoCollection();
  }

  public CallGraphBuilderBase setCodeLens(GraphLens codeLens) {
    this.codeLens = codeLens;
    return this;
  }

  public CallGraphBuilderBase setExcludeFieldReadWriteEdges() {
    includeFieldReadWriteEdges = false;
    return this;
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
    CycleEliminationResult cycleEliminationResult =
        cycleEliminator.breakCycles(nodesWithDeterministicOrder);
    timing.end();
    timing.end();
    assert cycleEliminator.breakCycles(nodesWithDeterministicOrder).numberOfRemovedCallEdges()
        == 0; // The cycles should be gone.

    return new CallGraph(nodes, cycleEliminationResult);
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

  Node getOrCreateNode(ProgramMethod method) {
    synchronized (nodes) {
      return nodes.computeIfAbsent(method.getReference(), ignore -> new Node(method));
    }
  }

  abstract boolean verifyAllMethodsWithCodeExists();
}
