// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class InParameterFlowPropagator {

  final AppView<AppInfoWithLiveness> appView;
  final MethodStateCollectionByReference methodStates;

  public InParameterFlowPropagator(
      AppView<AppInfoWithLiveness> appView, MethodStateCollectionByReference methodStates) {
    this.appView = appView;
    this.methodStates = methodStates;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    // Build a graph with an edge from parameter p -> parameter p' if all argument information for p
    // must be included in the argument information for p'.
    FlowGraph flowGraph = new FlowGraph(appView.appInfo().classes());

    // Build a worklist containing all the parameter nodes.
    Deque<ParameterNode> worklist = new ArrayDeque<>();
    flowGraph.forEachNode(worklist::add);

    // Repeatedly propagate argument information through edges in the flow graph until there are no
    // more changes.
    // TODO(b/190154391): Consider parallelizing the flow propagation. There are a few scenarios
    //  that need to be covered, such as (i) two threads could race to update the same parameter
    //  state, (ii) a thread may try to propagate a parameter state to its successors while
    //  another thread is trying to update the state of the parameter itself.
    // TODO(b/190154391): Consider a path p1 -> p2 -> p3 in the graph. If we process p2 first, then
    //  p3, and then p1, then the processing of p1 could cause p2 to change, which means that we
    //  need to reprocess p2 and then p3. If we always process leaves in the graph first, we would
    //  process p1, then p2, then p3, and then be done.
    // TODO(b/190154391): Prune the graph on-the-fly. If the argument information for a parameter
    //  becomes unknown, we could consider clearing its predecessors since none of the predecessors
    //  could contribute any information even if they change.
    while (!worklist.isEmpty()) {
      ParameterNode parameterNode = worklist.removeLast();
      parameterNode.unsetPending();
      propagate(
          parameterNode,
          affectedNode -> {
            // No need to enqueue the affected node if it is already in the worklist or if it does
            // not have any successors (i.e., the successor is a leaf).
            if (!affectedNode.isPending() && affectedNode.hasSuccessors()) {
              worklist.add(affectedNode);
              affectedNode.setPending();
            }
          });
    }

    // The algorithm only changes the parameter states of each monomorphic method state. In case any
    // of these method states have effectively become unknown, we replace them by the canonicalized
    // unknown method state.
    postProcessMethodStates(executorService);
  }

  private void propagate(
      ParameterNode parameterNode, Consumer<ParameterNode> affectedNodeConsumer) {
    ParameterState parameterState = parameterNode.getState();
    if (parameterState.isBottom()) {
      return;
    }
    for (ParameterNode successorNode : parameterNode.getSuccessors()) {
      successorNode.addState(
          appView, parameterState.asNonEmpty(), () -> affectedNodeConsumer.accept(successorNode));
    }
  }

  private void postProcessMethodStates(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(), this::postProcessMethodStates, executorService);
  }

  private void postProcessMethodStates(DexProgramClass clazz) {
    clazz.forEachProgramMethod(this::postProcessMethodState);
  }

  private void postProcessMethodState(ProgramMethod method) {
    ConcreteMethodState methodState = methodStates.get(method).asConcrete();
    if (methodState == null) {
      return;
    }
    assert methodState.isMonomorphic();
    boolean allUnknown = true;
    for (ParameterState parameterState : methodState.asMonomorphic().getParameterStates()) {
      if (parameterState.isBottom()) {
        methodStates.set(method, MethodState.bottom());
        return;
      }
      if (!parameterState.isUnknown()) {
        assert parameterState.isConcrete();
        allUnknown = false;
      }
    }
    if (allUnknown) {
      methodStates.set(method, MethodState.unknown());
    }
  }

  private class FlowGraph {

    private final Map<DexMethod, Int2ReferenceMap<ParameterNode>> nodes = new IdentityHashMap<>();

    public FlowGraph(Iterable<DexProgramClass> classes) {
      classes.forEach(this::add);
    }

    void forEachNode(Consumer<? super ParameterNode> consumer) {
      nodes.values().forEach(nodesForMethod -> nodesForMethod.values().forEach(consumer));
    }

    private void add(DexProgramClass clazz) {
      clazz.forEachProgramMethod(this::add);
    }

    private void add(ProgramMethod method) {
      MethodState methodState = methodStates.get(method);

      // No need to create nodes for parameters with no in-flow or no useful information.
      if (methodState.isBottom() || methodState.isUnknown()) {
        return;
      }

      // Add nodes for the parameters for which we have non-trivial information.
      ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
      List<ParameterState> parameterStates = monomorphicMethodState.getParameterStates();
      for (int parameterIndex = 0; parameterIndex < parameterStates.size(); parameterIndex++) {
        ParameterState parameterState = parameterStates.get(parameterIndex);
        add(method, parameterIndex, monomorphicMethodState, parameterState);
      }
    }

    private void add(
        ProgramMethod method,
        int parameterIndex,
        ConcreteMonomorphicMethodState methodState,
        ParameterState parameterState) {
      // No need to create nodes for parameters with no in-parameters and parameters we don't know
      // anything about.
      if (parameterState.isBottom() || parameterState.isUnknown()) {
        return;
      }

      ConcreteParameterState concreteParameterState = parameterState.asConcrete();

      // No need to create a node for a parameter that doesn't depend on any other parameters
      // (unless some other parameter depends on this parameter).
      if (!concreteParameterState.hasInParameters()) {
        return;
      }

      ParameterNode node = getOrCreateParameterNode(method, parameterIndex, methodState);
      for (MethodParameter inParameter : concreteParameterState.getInParameters()) {
        ProgramMethod enclosingMethod = getEnclosingMethod(inParameter);
        MethodState enclosingMethodState = getMethodState(enclosingMethod);
        if (enclosingMethodState.isBottom()) {
          // The current method is called from a dead method; no need to propagate any information
          // from the dead call site.
          continue;
        }

        if (enclosingMethodState.isUnknown()) {
          // The parameter depends on another parameter for which we don't know anything.
          node.clearPredecessors();
          node.setState(ParameterState.unknown());
          break;
        }

        assert enclosingMethodState.isConcrete();
        assert enclosingMethodState.asConcrete().isMonomorphic();

        ParameterNode predecessor =
            getOrCreateParameterNode(
                enclosingMethod,
                inParameter.getIndex(),
                enclosingMethodState.asConcrete().asMonomorphic());
        node.addPredecessor(predecessor);
      }

      if (!node.getState().isUnknown()) {
        assert node.getState() == concreteParameterState;
        node.setState(concreteParameterState.clearInParameters());
      }
    }

    private ParameterNode getOrCreateParameterNode(
        ProgramMethod method, int parameterIndex, ConcreteMonomorphicMethodState methodState) {
      Int2ReferenceMap<ParameterNode> parameterNodesForMethod =
          nodes.computeIfAbsent(method.getReference(), ignoreKey(Int2ReferenceOpenHashMap::new));
      return parameterNodesForMethod.compute(
          parameterIndex,
          (ignore, parameterNode) ->
              parameterNode != null
                  ? parameterNode
                  : new ParameterNode(
                      methodState, parameterIndex, method.getArgumentType(parameterIndex)));
    }

    private ProgramMethod getEnclosingMethod(MethodParameter methodParameter) {
      DexMethod methodReference = methodParameter.getMethod();
      return methodReference.lookupOnProgramClass(
          asProgramClassOrNull(appView.definitionFor(methodParameter.getMethod().getHolderType())));
    }

    private MethodState getMethodState(ProgramMethod method) {
      if (method == null) {
        // Conservatively return unknown if for some reason we can't find the method.
        assert false;
        return MethodState.unknown();
      }
      return methodStates.get(method);
    }
  }

  static class ParameterNode {

    private final ConcreteMonomorphicMethodState methodState;
    private final int parameterIndex;
    private final DexType parameterType;

    private final Set<ParameterNode> predecessors = Sets.newIdentityHashSet();
    private final Set<ParameterNode> successors = Sets.newIdentityHashSet();

    private boolean pending = true;

    ParameterNode(
        ConcreteMonomorphicMethodState methodState, int parameterIndex, DexType parameterType) {
      this.methodState = methodState;
      this.parameterIndex = parameterIndex;
      this.parameterType = parameterType;
    }

    void addPredecessor(ParameterNode predecessor) {
      predecessor.successors.add(this);
      predecessors.add(predecessor);
    }

    void clearPredecessors() {
      for (ParameterNode predecessor : predecessors) {
        predecessor.successors.remove(this);
      }
      predecessors.clear();
    }

    ParameterState getState() {
      return methodState.getParameterState(parameterIndex);
    }

    Set<ParameterNode> getSuccessors() {
      return successors;
    }

    boolean hasSuccessors() {
      return !successors.isEmpty();
    }

    boolean isPending() {
      return pending;
    }

    void addState(
        AppView<AppInfoWithLiveness> appView,
        NonEmptyParameterState parameterStateToAdd,
        Action onChangedAction) {
      ParameterState oldParameterState = getState();
      ParameterState newParameterState =
          oldParameterState.mutableJoin(
              appView, parameterStateToAdd, parameterType, onChangedAction);
      if (newParameterState != oldParameterState) {
        setState(newParameterState);
        onChangedAction.execute();
      }
    }

    void setPending() {
      assert !isPending();
      pending = true;
    }

    void setState(ParameterState parameterState) {
      methodState.setParameterState(parameterIndex, parameterState);
    }

    void unsetPending() {
      assert pending;
      pending = false;
    }
  }
}
