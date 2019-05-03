// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.IROrdering;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class MethodProcessingOrder {

  private final Deque<Collection<DexEncodedMethod>> waves;

  MethodProcessingOrder(AppView<?> appView, CallGraph callGraph) {
    this.waves = createWaves(appView, callGraph);
  }

  public static Deque<Collection<DexEncodedMethod>> createWaves(
      AppView<?> appView, CallGraph callGraph) {
    IROrdering shuffle = appView.options().testing.irOrdering;
    Deque<Collection<DexEncodedMethod>> waves = new ArrayDeque<>();

    Set<Node> nodes = callGraph.nodes;
    while (!nodes.isEmpty()) {
      waves.addLast(shuffle.order(extractLeaves(nodes)));
    }
    return waves;
  }

  private static Set<DexEncodedMethod> extractLeaves(Set<Node> nodes) {
    Set<DexEncodedMethod> leaves = Sets.newIdentityHashSet();
    Set<Node> removed = Sets.newIdentityHashSet();
    Iterator<Node> nodeIterator = nodes.iterator();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.next();
      if (node.isLeaf()) {
        leaves.add(node.method);
        nodeIterator.remove();
        removed.add(node);
      }
    }
    removed.forEach(Node::cleanForRemoval);
    return leaves;
  }

  /**
   * Applies the given method to all leaf nodes of the graph.
   *
   * <p>As second parameter, a predicate that can be used to decide whether another method is
   * processed at the same time is passed. This can be used to avoid races in concurrent processing.
   */
  public <E extends Exception> void forEachMethod(
      ThrowingBiConsumer<DexEncodedMethod, Predicate<DexEncodedMethod>, E> consumer,
      Action waveStart,
      Action waveDone,
      ExecutorService executorService)
      throws ExecutionException {
    while (!waves.isEmpty()) {
      Collection<DexEncodedMethod> wave = waves.removeFirst();
      assert wave.size() > 0;
      List<Future<?>> futures = new ArrayList<>();
      waveStart.execute();
      for (DexEncodedMethod method : wave) {
        futures.add(
            executorService.submit(
                () -> {
                  consumer.accept(method, wave::contains);
                  return null; // we want a Callable not a Runnable to be able to throw
                }));
      }
      ThreadUtils.awaitFutures(futures);
      waveDone.execute();
    }
  }
}
