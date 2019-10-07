// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
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
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MethodProcessor {

  private final CallSiteInformation callSiteInformation;
  private final Deque<Collection<DexEncodedMethod>> waves;

  MethodProcessor(AppView<AppInfoWithLiveness> appView, CallGraph callGraph) {
    this.callSiteInformation = callGraph.createCallSiteInformation(appView);
    this.waves = createWaves(appView, callGraph, callSiteInformation);
  }

  public CallSiteInformation getCallSiteInformation() {
    return callSiteInformation;
  }

  public static Deque<Collection<DexEncodedMethod>> createWaves(
      AppView<?> appView, CallGraph callGraph, CallSiteInformation callSiteInformation) {
    IROrdering shuffle = appView.options().testing.irOrdering;
    Deque<Collection<DexEncodedMethod>> waves = new ArrayDeque<>();

    Set<Node> nodes = callGraph.nodes;
    Set<DexEncodedMethod> reprocessing = Sets.newIdentityHashSet();
    while (!nodes.isEmpty()) {
      Set<DexEncodedMethod> wave = Sets.newIdentityHashSet();
      extractLeaves(
          nodes,
          leaf -> {
            wave.add(leaf.method);

            // Reprocess methods that invoke a method with a single call site.
            if (callSiteInformation.hasSingleCallSite(leaf.method.method)) {
              callGraph.cycleEliminationResult.forEachRemovedCaller(
                  leaf, caller -> reprocessing.add(caller.method));
            }
          });
      waves.addLast(shuffle.order(wave));
    }
    // TODO(b/127694949): Reprocess these methods using a general framework for reprocessing
    //  methods.
    if (!reprocessing.isEmpty()) {
      waves.addLast(shuffle.order(reprocessing));
    }
    return waves;
  }

  private static void extractLeaves(Set<Node> nodes, Consumer<Node> fn) {
    Set<Node> removed = Sets.newIdentityHashSet();
    Iterator<Node> nodeIterator = nodes.iterator();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.next();
      if (node.isLeaf()) {
        fn.accept(node);
        nodeIterator.remove();
        removed.add(node);
      }
    }
    removed.forEach(Node::cleanForRemoval);
  }

  /**
   * Applies the given method to all leaf nodes of the graph.
   *
   * <p>As second parameter, a predicate that can be used to decide whether another method is
   * processed at the same time is passed. This can be used to avoid races in concurrent processing.
   */
  public <E extends Exception> void forEachMethod(
      ThrowingBiConsumer<DexEncodedMethod, Predicate<DexEncodedMethod>, E> consumer,
      Consumer<Collection<DexEncodedMethod>> waveStart,
      Action waveDone,
      ExecutorService executorService)
      throws ExecutionException {
    while (!waves.isEmpty()) {
      Collection<DexEncodedMethod> wave = waves.removeFirst();
      assert wave.size() > 0;
      List<Future<?>> futures = new ArrayList<>();
      waveStart.accept(wave);
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
