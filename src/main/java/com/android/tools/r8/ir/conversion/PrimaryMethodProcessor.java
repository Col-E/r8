// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IROrdering;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingFunction;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * A {@link MethodProcessor} that processes methods in the whole program in a bottom-up manner,
 * i.e., from leaves to roots.
 */
class PrimaryMethodProcessor implements MethodProcessor {

  interface WaveStartAction {

    void notifyWaveStart(Collection<DexEncodedMethod> wave);
  }

  private final CallSiteInformation callSiteInformation;
  private final PostMethodProcessor.Builder postMethodProcessorBuilder;
  private final Deque<Collection<DexEncodedMethod>> waves;
  private Collection<DexEncodedMethod> wave;

  private PrimaryMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      CallGraph callGraph) {
    this.callSiteInformation = callGraph.createCallSiteInformation(appView);
    this.postMethodProcessorBuilder = postMethodProcessorBuilder;
    this.waves = createWaves(appView, callGraph, callSiteInformation);
  }

  static PrimaryMethodProcessor create(
      AppView<AppInfoWithLiveness> appView,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    CallGraph callGraph = CallGraph.builder(appView).build(executorService, timing);
    return new PrimaryMethodProcessor(appView, postMethodProcessorBuilder, callGraph);
  }

  @Override
  public Phase getPhase() {
    return Phase.PRIMARY;
  }

  @Override
  public boolean shouldApplyCodeRewritings(DexEncodedMethod method) {
    assert !wave.contains(method);
    return !method.isProcessed();
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    return callSiteInformation;
  }

  private Deque<Collection<DexEncodedMethod>> createWaves(
      AppView<?> appView, CallGraph callGraph, CallSiteInformation callSiteInformation) {
    InternalOptions options = appView.options();
    IROrdering shuffle = options.testing.irOrdering;
    Deque<Collection<DexEncodedMethod>> waves = new ArrayDeque<>();

    Set<Node> nodes = callGraph.nodes;
    Set<DexEncodedMethod> reprocessing = Sets.newIdentityHashSet();
    int waveCount = 1;
    while (!nodes.isEmpty()) {
      Set<DexEncodedMethod> wave = callGraph.extractLeaves();
      for (DexEncodedMethod method : wave) {
        if (callSiteInformation.hasSingleCallSite(method.method)) {
          callGraph.cycleEliminationResult.forEachRemovedCaller(method, reprocessing::add);
        }
      }
      waves.addLast(shuffle.order(wave));
      if (Log.ENABLED && Log.isLoggingEnabledFor(PrimaryMethodProcessor.class)) {
        Log.info(getClass(), "Wave #%d: %d", waveCount++, wave.size());
      }
    }
    if (!reprocessing.isEmpty()) {
      postMethodProcessorBuilder.put(reprocessing);
    }
    options.testing.waveModifier.accept(waves);
    return waves;
  }

  @Override
  public boolean isProcessedConcurrently(DexEncodedMethod method) {
    return wave != null && wave.contains(method);
  }

  /**
   * Applies the given method to all leaf nodes of the graph.
   *
   * <p>As second parameter, a predicate that can be used to decide whether another method is
   * processed at the same time is passed. This can be used to avoid races in concurrent processing.
   */
  <E extends Exception> void forEachMethod(
      ThrowingFunction<DexEncodedMethod, Timing, E> consumer,
      WaveStartAction waveStartAction,
      Consumer<Collection<DexEncodedMethod>> waveDone,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    TimingMerger merger =
        timing.beginMerger("primary-processor", ThreadUtils.getNumberOfThreads(executorService));
    while (!waves.isEmpty()) {
      wave = waves.removeFirst();
      assert wave.size() > 0;
      waveStartAction.notifyWaveStart(wave);
      merger.add(
          ThreadUtils.processItemsWithResults(
              wave,
              method -> {
                Timing time = consumer.apply(method);
                time.end();
                return time;
              },
              executorService));
      waveDone.accept(wave);
    }
    merger.end();
  }
}
