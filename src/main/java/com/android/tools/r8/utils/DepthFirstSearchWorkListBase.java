// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.DepthFirstSearchWorkListBase.ProcessingState.FINISHED;
import static com.android.tools.r8.utils.DepthFirstSearchWorkListBase.ProcessingState.NOT_PROCESSED;
import static com.android.tools.r8.utils.DepthFirstSearchWorkListBase.ProcessingState.WAITING;

import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.DFSNodeImpl;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class DepthFirstSearchWorkListBase<N, T extends DFSNodeImpl<N>, TB, TC> {

  public interface DFSNode<N> {
    N getNode();

    boolean seenAndNotProcessed();
  }

  public interface DFSNodeWithState<N, S> extends DFSNode<N> {

    S getState();

    void setState(S backtrackState);

    boolean hasState();
  }

  enum ProcessingState {
    NOT_PROCESSED,
    WAITING,
    FINISHED;
  }

  static class DFSNodeImpl<N> implements DFSNode<N> {

    private final N node;
    private ProcessingState processingState = NOT_PROCESSED;

    private DFSNodeImpl(N node) {
      this.node = node;
    }

    boolean isNotProcessed() {
      return processingState == NOT_PROCESSED;
    }

    boolean isFinished() {
      return processingState == FINISHED;
    }

    void setWaiting() {
      processingState = WAITING;
    }

    void setFinished() {
      assert processingState != FINISHED;
      processingState = FINISHED;
    }

    @Override
    public N getNode() {
      return node;
    }

    @Override
    public boolean seenAndNotProcessed() {
      return processingState == WAITING;
    }
  }

  static class DFSNodeWithStateImpl<N, S> extends DFSNodeImpl<N> implements DFSNodeWithState<N, S> {

    private S state;

    private DFSNodeWithStateImpl(N node) {
      super(node);
    }

    @Override
    public S getState() {
      return state;
    }

    @Override
    public void setState(S state) {
      this.state = state;
    }

    @Override
    public boolean hasState() {
      return state != null;
    }
  }

  private final ArrayDeque<T> workList = new ArrayDeque<>();
  // This map is necessary ensure termination since we embed nodes into nodes with state.
  private final Map<N, T> nodeToNodeWithStateMap = new IdentityHashMap<>();

  abstract T createDfsNode(N node);

  /** The initial processing of a node during forward search */
  abstract TraversalContinuation<TB, TC> internalOnVisit(T node);

  /** The joining of state during backtracking of the algorithm. */
  abstract TraversalContinuation<TB, TC> internalOnJoin(T node);

  protected abstract List<TC> getFinalStateForRoots(Collection<? extends N> roots);

  final T internalEnqueueNode(N value) {
    T dfsNode = nodeToNodeWithStateMap.computeIfAbsent(value, this::createDfsNode);
    if (dfsNode.isNotProcessed()) {
      workList.addLast(dfsNode);
    }
    return dfsNode;
  }

  protected T getNodeStateForNode(N value) {
    return nodeToNodeWithStateMap.get(value);
  }

  public final TraversalContinuation<TB, TC> run(N root) {
    return run(Collections.singletonList(root)).map(Function.identity(), results -> results.get(0));
  }

  @SafeVarargs
  public final TraversalContinuation<TB, List<TC>> run(N... roots) {
    return run(Arrays.asList(roots));
  }

  public final TraversalContinuation<TB, List<TC>> run(Collection<? extends N> roots) {
    roots.forEach(this::internalEnqueueNode);
    while (!workList.isEmpty()) {
      T node = workList.removeLast();
      if (node.isFinished()) {
        continue;
      }
      TraversalContinuation<TB, TC> continuation;
      if (node.isNotProcessed()) {
        workList.addLast(node);
        node.setWaiting();
        continuation = internalOnVisit(node);
      } else {
        assert node.seenAndNotProcessed();
        continuation = internalOnJoin(node);
        node.setFinished();
      }
      if (continuation.shouldBreak()) {
        return TraversalContinuation.doBreak(continuation.asBreak().getValue());
      }
    }

    return TraversalContinuation.doContinue(getFinalStateForRoots(roots));
  }

  public abstract static class DepthFirstSearchWorkList<N, TB, TC>
      extends DepthFirstSearchWorkListBase<N, DFSNodeImpl<N>, TB, TC> {

    /**
     * The initial processing of the node when visiting the first time during the depth first
     * search.
     *
     * @param node The current node.
     * @param childNodeConsumer A consumer for adding child nodes. If an element has been seen
     *     before but not finished there is a cycle.
     * @return A value describing if the DFS algorithm should continue to run.
     */
    protected abstract TraversalContinuation<TB, TC> process(
        DFSNode<N> node, Function<N, DFSNode<N>> childNodeConsumer);

    @Override
    DFSNodeImpl<N> createDfsNode(N node) {
      return new DFSNodeImpl<>(node);
    }

    @Override
    TraversalContinuation<TB, TC> internalOnVisit(DFSNodeImpl<N> node) {
      return process(node, this::internalEnqueueNode);
    }

    @Override
    protected TraversalContinuation<TB, TC> internalOnJoin(DFSNodeImpl<N> node) {
      return joiner(node);
    }

    public TraversalContinuation<TB, TC> joiner(DFSNode<N> node) {
      // Override to be notified during callback.
      return TraversalContinuation.doContinue();
    }

    @Override
    protected List<TC> getFinalStateForRoots(Collection<? extends N> roots) {
      return null;
    }
  }

  public abstract static class StatefulDepthFirstSearchWorkList<N, S, TB>
      extends DepthFirstSearchWorkListBase<N, DFSNodeWithStateImpl<N, S>, TB, S> {

    private final Map<DFSNodeWithStateImpl<N, S>, List<DFSNodeWithState<N, S>>> childStateMap =
        new IdentityHashMap<>();

    /**
     * The initial processing of the node when visiting the first time during the depth first
     * search.
     *
     * @param node The current node.
     * @param childNodeConsumer A consumer for adding child nodes. If an element has been seen
     *     before but not finished there is a cycle.
     * @return A value describing if the DFS algorithm should continue to run.
     */
    protected abstract TraversalContinuation<TB, S> process(
        DFSNodeWithState<N, S> node, Function<N, DFSNodeWithState<N, S>> childNodeConsumer);

    /**
     * The joining of state during backtracking of the algorithm.
     *
     * @param node The current node
     * @param childStates The already computed child states.
     * @return A value describing if the DFS algorithm should continue to run.
     */
    protected abstract TraversalContinuation<TB, S> joiner(
        DFSNodeWithState<N, S> node, List<DFSNodeWithState<N, S>> childStates);

    @Override
    DFSNodeWithStateImpl<N, S> createDfsNode(N node) {
      return new DFSNodeWithStateImpl<>(node);
    }

    @Override
    TraversalContinuation<TB, S> internalOnVisit(DFSNodeWithStateImpl<N, S> node) {
      List<DFSNodeWithState<N, S>> childStates = new ArrayList<>();
      List<DFSNodeWithState<N, S>> removedChildStates = childStateMap.put(node, childStates);
      assert removedChildStates == null;
      return process(
          node,
          successor -> {
            DFSNodeWithStateImpl<N, S> successorNode = internalEnqueueNode(successor);
            childStates.add(successorNode);
            return successorNode;
          });
    }

    @Override
    protected TraversalContinuation<TB, S> internalOnJoin(DFSNodeWithStateImpl<N, S> node) {
      return joiner(
          node,
          childStateMap.computeIfAbsent(
              node,
              n -> {
                assert false : "Unexpected joining of not visited node";
                return new ArrayList<>();
              }));
    }

    @Override
    public List<S> getFinalStateForRoots(Collection<? extends N> roots) {
      return ListUtils.map(roots, root -> getNodeStateForNode(root).state);
    }
  }
}
