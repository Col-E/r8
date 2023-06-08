// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallGraph;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.ir.conversion.callgraph.Node;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link MethodProcessor} that processes methods in the whole program in a bottom-up manner,
 * i.e., from leaves to roots.
 */
public class PrimaryMethodProcessor extends MethodProcessorWithWave {

  interface WaveStartAction {

    void notifyWaveStart(ProgramMethodSet wave);
  }

  interface WaveDoneAction {

    void notifyWaveDone(ProgramMethodSet wave, ExecutorService executorService)
        throws ExecutionException;
  }

  private final AppView<?> appView;
  private final CallSiteInformation callSiteInformation;
  private final MethodProcessorEventConsumer eventConsumer;
  private final Deque<ProgramMethodSet> waves;

  private ProcessorContext processorContext;

  private PrimaryMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      CallGraph callGraph,
      MethodProcessorEventConsumer eventConsumer) {
    this.appView = appView;
    this.callSiteInformation = callGraph.createCallSiteInformation(appView);
    this.eventConsumer = eventConsumer;
    this.waves = createWaves(appView, callGraph);
  }

  static PrimaryMethodProcessor create(
      AppView<AppInfoWithLiveness> appView,
      MethodProcessorEventConsumer eventConsumer,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    CallGraph callGraph = CallGraph.builder(appView).build(executorService, timing);
    return new PrimaryMethodProcessor(appView, callGraph, eventConsumer);
  }

  @Override
  public MethodProcessingContext createMethodProcessingContext(ProgramMethod method) {
    return processorContext.createMethodProcessingContext(method);
  }

  @Override
  public MethodProcessorEventConsumer getEventConsumer() {
    return eventConsumer;
  }

  @Override
  public boolean isPrimaryMethodProcessor() {
    return true;
  }

  @Override
  public PrimaryMethodProcessor asPrimaryMethodProcessor() {
    return this;
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    assert !wave.contains(method);
    return !method.getDefinition().isProcessed();
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    return callSiteInformation;
  }

  private Deque<ProgramMethodSet> createWaves(AppView<?> appView, CallGraph callGraph) {
    InternalOptions options = appView.options();
    Deque<ProgramMethodSet> waves = new ArrayDeque<>();
    Collection<Node> nodes = callGraph.getNodes();
    int waveCount = 1;
    while (!nodes.isEmpty()) {
      ProgramMethodSet wave = callGraph.extractLeaves();
      waves.addLast(wave);
    }
    options.testing.waveModifier.accept(waves);
    return waves;
  }

  @FunctionalInterface
  public interface MethodAction<E extends Exception> {
    Timing apply(ProgramMethod method, MethodProcessingContext methodProcessingContext) throws E;
  }

  /**
   * Applies the given method to all leaf nodes of the graph.
   *
   * <p>As second parameter, a predicate that can be used to decide whether another method is
   * processed at the same time is passed. This can be used to avoid races in concurrent processing.
   */
  <E extends Exception> void forEachMethod(
      MethodAction<E> consumer,
      WaveStartAction waveStartAction,
      WaveDoneAction waveDoneAction,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    TimingMerger merger = timing.beginMerger("primary-processor", executorService);
    while (!waves.isEmpty()) {
      processorContext = appView.createProcessorContext();
      wave = waves.removeFirst();
      assert !wave.isEmpty();
      assert waveExtension.isEmpty();
      do {
        waveStartAction.notifyWaveStart(wave);
        Collection<Timing> timings =
            ThreadUtils.processItemsWithResults(
                wave,
                method -> {
                  Timing time = consumer.apply(method, createMethodProcessingContext(method));
                  time.end();
                  return time;
                },
                executorService);
        merger.add(timings);
        waveDoneAction.notifyWaveDone(wave, executorService);
        prepareForWaveExtensionProcessing();
      } while (!wave.isEmpty());
    }
    merger.end();
  }
}
