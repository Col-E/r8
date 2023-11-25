// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ComposableOptimizationPass {

  private final AppView<AppInfoWithLiveness> appView;
  private final PrimaryR8IRConverter converter;

  private ComposableOptimizationPass(
      AppView<AppInfoWithLiveness> appView, PrimaryR8IRConverter converter) {
    this.appView = appView;
    this.converter = converter;
  }

  public static void run(
      AppView<AppInfoWithLiveness> appView, PrimaryR8IRConverter converter, Timing timing) {
    InternalOptions options = appView.options();
    if (!options.isOptimizing() || !options.isShrinking()) {
      return;
    }
    TestingOptions testingOptions = options.getTestingOptions();
    if (!testingOptions.enableComposableOptimizationPass
        || !testingOptions.modelUnknownChangedAndDefaultArgumentsToComposableFunctions) {
      return;
    }
    timing.time(
        "ComposableOptimizationPass",
        () -> new ComposableOptimizationPass(appView, converter).processWaves());
  }

  void processWaves() {
    ComposableCallGraph callGraph = ComposableCallGraph.builder(appView).build();
    ComposeMethodProcessor methodProcessor =
        new ComposeMethodProcessor(appView, callGraph, converter);
    Set<ComposableCallGraphNode> wave = createInitialWave(callGraph);
    while (!wave.isEmpty()) {
      Set<ComposableCallGraphNode> optimizedComposableFunctions = methodProcessor.processWave(wave);
      wave = createNextWave(methodProcessor, optimizedComposableFunctions);
    }
  }

  // TODO(b/302483644): Should we skip root @Composable functions that don't have any nested
  //  @Composable functions (?).
  private Set<ComposableCallGraphNode> computeComposableRoots(ComposableCallGraph callGraph) {
    Set<ComposableCallGraphNode> composableRoots = Sets.newIdentityHashSet();
    callGraph.forEachNode(
        node -> {
          if (!node.isComposable()
              || Iterables.any(node.getCallers(), ComposableCallGraphNode::isComposable)) {
            // This is not a @Composable root.
            return;
          }
          if (node.getCallers().isEmpty()) {
            // Don't include root @Composable functions that are never called. These are either kept
            // or will be removed in tree shaking.
            return;
          }
          composableRoots.add(node);
        });
    return composableRoots;
  }

  private Set<ComposableCallGraphNode> createInitialWave(ComposableCallGraph callGraph) {
    Set<ComposableCallGraphNode> wave = Sets.newIdentityHashSet();
    Set<ComposableCallGraphNode> composableRoots = computeComposableRoots(callGraph);
    composableRoots.forEach(composableRoot -> wave.addAll(composableRoot.getCallers()));
    return wave;
  }

  // TODO(b/302483644): Consider repeatedly extracting the roots from the graph similar to the way
  //  we extract leaves in the primary optimization pass.
  private static Set<ComposableCallGraphNode> createNextWave(
      ComposeMethodProcessor methodProcessor,
      Set<ComposableCallGraphNode> optimizedComposableFunctions) {
    Set<ComposableCallGraphNode> nextWave =
        SetUtils.newIdentityHashSet(optimizedComposableFunctions);

    // If the new wave contains two @Composable functions where one calls the other, then defer the
    // processing of the callee to a later wave, to ensure that we have seen all of its callers
    // before processing the callee.
    List<ComposableCallGraphNode> deferredComposableFunctions = new ArrayList<>();
    nextWave.forEach(
        node -> {
          if (SetUtils.containsAnyOf(nextWave, node.getCallers())) {
            deferredComposableFunctions.add(node);
          }
        });
    deferredComposableFunctions.forEach(nextWave::remove);

    // To optimize the @Composable functions that are called from the @Composable functions of the
    // next wave in the wave after that, we need to include their callers in the next wave as well.
    Set<ComposableCallGraphNode> callersOfCalledComposableFunctions = Sets.newIdentityHashSet();
    nextWave.forEach(
        node ->
            node.forEachComposableCallee(
                callee -> callersOfCalledComposableFunctions.addAll(callee.getCallers())));
    nextWave.addAll(callersOfCalledComposableFunctions);
    nextWave.removeIf(methodProcessor::isProcessed);
    return nextWave;
  }
}
