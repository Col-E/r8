// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.unusedarguments;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.dfs.DFSStack;
import com.android.tools.r8.utils.dfs.DFSWorklistItem;
import com.android.tools.r8.utils.dfs.DFSWorklistItem.NewlyVisitedDFSWorklistItem;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class EffectivelyUnusedArgumentsGraph {

  private final AppView<AppInfoWithLiveness> appView;

  private final Map<MethodParameter, EffectivelyUnusedArgumentsGraphNode> nodes = new HashMap<>();

  private EffectivelyUnusedArgumentsGraph(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public static EffectivelyUnusedArgumentsGraph create(
      AppView<AppInfoWithLiveness> appView,
      Map<MethodParameter, Set<MethodParameter>> constraints,
      MethodStateCollectionByReference methodStates) {
    EffectivelyUnusedArgumentsGraph graph = new EffectivelyUnusedArgumentsGraph(appView);
    constraints.forEach(
        (methodParameter, constraintsForMethodParameter) -> {
          EffectivelyUnusedArgumentsGraphNode node = graph.getOrCreateNode(methodParameter);
          for (MethodParameter constraint : constraintsForMethodParameter) {
            graph.addConstraintEdge(node, constraint, constraints, methodStates);
          }
        });
    return graph;
  }

  void addConstraintEdge(
      EffectivelyUnusedArgumentsGraphNode node,
      MethodParameter constraint,
      Map<MethodParameter, Set<MethodParameter>> constraints,
      MethodStateCollectionByReference methodStates) {
    ProgramMethod dependencyMethod =
        asProgramMethodOrNull(appView.definitionFor(constraint.getMethod()));
    if (dependencyMethod == null) {
      assert false;
      node.setUnoptimizable();
      return;
    }

    // A nullable method parameter cannot be effectively unused if it is used as the receiver in an
    // invoke (or we cannot preserve NPE semantics).
    if (dependencyMethod.getDefinition().isInstance()
        && constraint.getIndex() == 0
        && node.isNullable(methodStates)) {
      node.setUnoptimizable();
      return;
    }

    // If the successor parameter does not have any constraints, then the successor is not subject
    // to effectively unused argument removal. In this case, the successor can only be removed if it
    // is truly unused.
    if (!constraints.containsKey(constraint)) {
      MethodOptimizationInfo optimizationInfo = dependencyMethod.getOptimizationInfo();
      if (!optimizationInfo.hasUnusedArguments()
          || !optimizationInfo.getUnusedArguments().get(constraint.getIndex())) {
        node.setUnoptimizable();
      }
      return;
    }

    EffectivelyUnusedArgumentsGraphNode successor = getOrCreateNode(constraint, dependencyMethod);
    if (node != successor) {
      node.addSuccessor(successor);
    }
  }

  Collection<EffectivelyUnusedArgumentsGraphNode> getNodes() {
    return nodes.values();
  }

  EffectivelyUnusedArgumentsGraphNode getOrCreateNode(MethodParameter parameter) {
    ProgramMethod method = asProgramMethodOrNull(appView.definitionFor(parameter.getMethod()));
    return method != null ? getOrCreateNode(parameter, method) : null;
  }

  EffectivelyUnusedArgumentsGraphNode getOrCreateNode(
      MethodParameter parameter, ProgramMethod method) {
    return nodes.computeIfAbsent(
        parameter, p -> new EffectivelyUnusedArgumentsGraphNode(method, p.getIndex()));
  }

  void remove(EffectivelyUnusedArgumentsGraphNode node) {
    assert node.getSuccessors().isEmpty();
    assert node.getPredecessors().isEmpty();
    MethodParameter methodParameter =
        new MethodParameter(node.getMethod().getReference(), node.getArgumentIndex());
    EffectivelyUnusedArgumentsGraphNode removed = nodes.remove(methodParameter);
    assert removed == node;
  }

  void removeClosedCycles(Consumer<EffectivelyUnusedArgumentsGraphNode> reprocess) {
    Set<EffectivelyUnusedArgumentsGraphNode> seen = Sets.newIdentityHashSet();
    for (EffectivelyUnusedArgumentsGraphNode root : getNodes()) {
      if (seen.contains(root)) {
        continue;
      }
      DFSStack<EffectivelyUnusedArgumentsGraphNode> stack = DFSStack.createIdentityStack();
      Deque<DFSWorklistItem<EffectivelyUnusedArgumentsGraphNode>> worklist = new ArrayDeque<>();
      worklist.add(new NewlyVisitedDFSWorklistItem<>(root));
      while (!worklist.isEmpty()) {
        DFSWorklistItem<EffectivelyUnusedArgumentsGraphNode> item = worklist.removeLast();
        stack.handle(item);
        if (item.isFullyVisited()) {
          continue;
        }
        EffectivelyUnusedArgumentsGraphNode node = item.getValue();
        seen.add(node);
        worklist.add(item.asNewlyVisited().toFullyVisited());
        node.getSuccessors()
            .removeIf(
                successor -> {
                  if (stack.contains(successor)) {
                    Deque<EffectivelyUnusedArgumentsGraphNode> cycle =
                        stack.getCycleStartingAt(successor);
                    boolean isClosedCycle =
                        Iterables.all(cycle, member -> member.getSuccessors().size() == 1);
                    if (isClosedCycle) {
                      // Remove edge and reprocess this node, since it is now eligible for unused
                      // argument removal.
                      boolean removed = successor.getPredecessors().remove(node);
                      assert removed;
                      reprocess.accept(node);
                      // Return true to remove the successor from the successors of the current
                      // node (to prevent ConcurrentModificationException).
                      return true;
                    }
                  } else {
                    worklist.add(new NewlyVisitedDFSWorklistItem<>(successor));
                  }
                  return false;
                });
      }
    }
  }

  boolean verifyContains(EffectivelyUnusedArgumentsGraphNode node) {
    MethodParameter methodParameter =
        new MethodParameter(node.getMethod().getReference(), node.getArgumentIndex());
    return nodes.containsKey(methodParameter);
  }

  void removeUnoptimizableNodes() {
    WorkList<EffectivelyUnusedArgumentsGraphNode> worklist = WorkList.newIdentityWorkList();
    for (EffectivelyUnusedArgumentsGraphNode node : getNodes()) {
      if (node.isUnoptimizable()) {
        worklist.addIfNotSeen(node);
      }
    }
    while (worklist.hasNext()) {
      EffectivelyUnusedArgumentsGraphNode node = worklist.next();
      worklist.addIfNotSeen(node.getPredecessors());
      node.cleanForRemoval();
      remove(node);
    }
  }
}
