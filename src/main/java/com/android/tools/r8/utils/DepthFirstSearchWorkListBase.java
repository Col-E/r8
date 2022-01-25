// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.DepthFirstSearchWorkListBase.ProcessingState.FINISHED;
import static com.android.tools.r8.utils.DepthFirstSearchWorkListBase.ProcessingState.NOT_PROCESSED;
import static com.android.tools.r8.utils.DepthFirstSearchWorkListBase.ProcessingState.WAITING;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.DFSNode;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.DFSNodeImpl;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class DepthFirstSearchWorkListBase<
    N, TExp extends DFSNode<N>, TImpl extends DFSNodeImpl<N>> {

  public interface DFSNode<N> {
    N getNode();

    boolean seenAndNotProcessed();
  }

  public interface DFSNodeWithState<N, S> extends DFSNode<N> {

    S getState();

    void setState(S backtrackState);
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
  }

  private final ArrayDeque<TImpl> workList = new ArrayDeque<>();
  private final Map<N, TImpl> stateMap = new IdentityHashMap<>();
  private final Map<TImpl, List<TExp>> childStateMap = new IdentityHashMap<>();

  abstract TImpl newNode(N node);

  abstract boolean isStateful();

  /**
   * The initial processing of the node when visiting the first time during the depth first search.
   *
   * @param node The current node.
   * @param childNodeConsumer A consumer for adding child nodes. If an element has been seen before
   *     but not finished there is a cycle.
   * @return A value describing if the DFS algorithm should continue to run.
   */
  protected abstract TraversalContinuation process(TExp node, Function<N, TExp> childNodeConsumer);

  /**
   * The joining of state during backtracking of the algorithm.
   *
   * @param node The current node
   * @param childStates The already computed child states.
   * @return A value describing if the DFS algorithm should continue to run.
   */
  TraversalContinuation joiner(TExp node, List<TExp> childStates) {
    throw new Unreachable("Should not be called");
  }

  @SafeVarargs
  public final TraversalContinuation run(N... roots) {
    return run(Arrays.asList(roots));
  }

  @SuppressWarnings("unchecked")
  public final TraversalContinuation run(Collection<N> roots) {
    for (N root : roots) {
      TImpl newNode = newNode(root);
      stateMap.put(root, newNode);
      workList.addLast(newNode);
    }
    TraversalContinuation continuation = TraversalContinuation.CONTINUE;
    while (!workList.isEmpty()) {
      TImpl node = workList.removeLast();
      if (node.isFinished()) {
        continue;
      }
      TExp exposed = (TExp) node;
      if (node.isNotProcessed()) {
        workList.addLast(node);
        List<TExp> childStates =
            isStateful()
                ? childStateMap.computeIfAbsent(node, FunctionUtils.ignoreArgument(ArrayList::new))
                : null;
        node.setWaiting();
        continuation =
            process(
                exposed,
                childNode -> {
                  TImpl childImpl = stateMap.computeIfAbsent(childNode, this::newNode);
                  if (childImpl.isNotProcessed()) {
                    workList.addLast(childImpl);
                  }
                  TExp childExp = (TExp) childImpl;
                  if (childStates != null) {
                    childStates.add(childExp);
                  }
                  return (TExp) childImpl;
                });
      } else {
        assert node.seenAndNotProcessed();
        if (isStateful()) {
          continuation = joiner((TExp) node, childStateMap.get(node));
        }
        node.setFinished();
      }
      if (continuation.shouldBreak()) {
        return continuation;
      }
    }
    assert continuation.shouldBreak() || stateMap.values().stream().allMatch(TImpl::isFinished);
    return continuation;
  }

  @SuppressWarnings("unchecked")
  public void unwindUntilInclusive(TExp exp, Consumer<TExp> nodesOnPathConsumer) {
    assert !workList.isEmpty();
    TImpl startOfLoop = (TImpl) exp;
    Iterator<TImpl> descendingIterator = workList.descendingIterator();
    while (descendingIterator.hasNext()) {
      TImpl next = descendingIterator.next();
      if (!next.seenAndNotProcessed()) {
        nodesOnPathConsumer.accept((TExp) next);
      }
      if (next == startOfLoop) {
        return;
      }
    }
  }

  public abstract static class DepthFirstSearchWorkList<N>
      extends DepthFirstSearchWorkListBase<N, DFSNode<N>, DFSNodeImpl<N>> {

    @Override
    DFSNodeImpl<N> newNode(N node) {
      return new DFSNodeImpl<>(node);
    }

    @Override
    boolean isStateful() {
      return false;
    }
  }

  public abstract static class StatefulDepthFirstSearchWorkListBase<N, S>
      extends DepthFirstSearchWorkListBase<N, DFSNodeWithState<N, S>, DFSNodeWithStateImpl<N, S>> {

    @Override
    DFSNodeWithStateImpl<N, S> newNode(N node) {
      return new DFSNodeWithStateImpl<>(node);
    }

    @Override
    boolean isStateful() {
      return true;
    }

    @Override
    public abstract TraversalContinuation joiner(
        DFSNodeWithState<N, S> node, List<DFSNodeWithState<N, S>> childStates);
  }
}
