// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.PrimaryMethodProcessor.MethodAction;
import com.android.tools.r8.ir.conversion.callgraph.CallGraph;
import com.android.tools.r8.ir.conversion.callgraph.PartialCallGraphBuilder;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DeterminismChecker;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PostMethodProcessor extends MethodProcessorWithWave {

  private final MethodProcessorEventConsumer eventConsumer;
  private final ProcessorContext processorContext;
  private final Deque<ProgramMethodSet> waves;
  private final ProgramMethodSet processed = ProgramMethodSet.create();

  private PostMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      CallGraph callGraph,
      MethodProcessorEventConsumer eventConsumer) {
    this.eventConsumer = eventConsumer;
    this.processorContext = appView.createProcessorContext();
    this.waves = createWaves(callGraph);
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
  public boolean isPostMethodProcessor() {
    return true;
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    assert !wave.contains(method);
    return !processed.contains(method);
  }

  public static class Builder {

    private final LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodsToReprocessBuilder;

    Builder(GraphLens graphLensForPrimaryOptimizationPass) {
      this.methodsToReprocessBuilder =
          LongLivedProgramMethodSetBuilder.createConcurrentForIdentitySet(
              graphLensForPrimaryOptimizationPass);
    }

    public void add(ProgramMethod method) {
      methodsToReprocessBuilder.add(method);
    }

    public void add(ProgramMethod method, GraphLens currentGraphLens) {
      methodsToReprocessBuilder.add(method, currentGraphLens);
    }

    public void addAll(Collection<ProgramMethod> methods, GraphLens currentGraphLens) {
      methods.forEach(method -> add(method, currentGraphLens));
    }

    public boolean contains(ProgramMethod method, GraphLens currentGraphLens) {
      return methodsToReprocessBuilder.contains(method, currentGraphLens);
    }

    public PostMethodProcessor.Builder merge(
        LongLivedProgramMethodSetBuilder<ProgramMethodSet> otherMethodsToReprocessBuilder) {
      methodsToReprocessBuilder.merge(otherMethodsToReprocessBuilder);
      return this;
    }

    public void put(ProgramMethodSet methodsToRevisit) {
      methodsToRevisit.forEach(this::add);
    }

    public void remove(ProgramMethod method, GraphLens graphLens) {
      methodsToReprocessBuilder.remove(method.getReference(), graphLens);
    }

    public PostMethodProcessor.Builder removeAll(Collection<DexMethod> methods) {
      methodsToReprocessBuilder.removeAll(methods);
      return this;
    }

    // Some optimizations may change methods, creating new instances of the encoded methods with a
    // new signature. The compiler needs to update the set of methods that must be reprocessed
    // according to the graph lens.
    public PostMethodProcessor.Builder rewrittenWithLens(AppView<AppInfoWithLiveness> appView) {
      methodsToReprocessBuilder.rewrittenWithLens(appView);
      return this;
    }

    public PostMethodProcessor.Builder rewrittenWithLens(GraphLens graphLens) {
      methodsToReprocessBuilder.rewrittenWithLens(graphLens);
      return this;
    }

    PostMethodProcessor build(
        AppView<AppInfoWithLiveness> appView,
        MethodProcessorEventConsumer eventConsumer,
        ExecutorService executorService,
        Timing timing)
        throws ExecutionException {
      Set<DexMethod> reprocessMethods = appView.appInfo().getReprocessMethods();
      if (!reprocessMethods.isEmpty()) {
        ProgramMethodSet set = ProgramMethodSet.create();
        reprocessMethods.forEach(
            reference -> {
              DexProgramClass clazz = asProgramClassOrNull(appView.definitionForHolder(reference));
              DexEncodedMethod definition = reference.lookupOnClass(clazz);
              if (definition != null) {
                set.createAndAdd(clazz, definition);
              }
            });
        put(set);
      }
      if (methodsToReprocessBuilder.isEmpty()) {
        // Nothing to revisit.
        return null;
      }
      ProgramMethodSet methodsToReprocess = methodsToReprocessBuilder.build(appView);
      assert !appView.options().debug
          || methodsToReprocess.stream()
              .allMatch(methodToReprocess -> methodToReprocess.getDefinition().isD8R8Synthesized());
      CallGraph callGraph =
          new PartialCallGraphBuilder(appView, methodsToReprocess).build(executorService, timing);
      return new PostMethodProcessor(appView, callGraph, eventConsumer);
    }

    public void dump(DeterminismChecker determinismChecker) throws IOException {
      determinismChecker.accept(methodsToReprocessBuilder::dump);
    }
  }

  private Deque<ProgramMethodSet> createWaves(CallGraph callGraph) {
    Deque<ProgramMethodSet> waves = new ArrayDeque<>();
    int waveCount = 1;
    while (!callGraph.isEmpty()) {
      ProgramMethodSet wave = callGraph.extractLeaves();
      waves.addLast(wave);
    }
    return waves;
  }

  <E extends Exception> void forEachMethod(
      MethodAction<E> consumer,
      OptimizationFeedbackDelayed feedback,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    TimingMerger merger = timing.beginMerger("secondary-processor", executorService);
    while (!waves.isEmpty()) {
      wave = waves.removeFirst();
      assert !wave.isEmpty();
      assert waveExtension.isEmpty();
      do {
        assert feedback.noUpdatesLeft();
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
        feedback.updateVisibleOptimizationInfo();
        processed.addAll(wave);
        prepareForWaveExtensionProcessing();
      } while (!wave.isEmpty());
    }
    merger.end();
  }
}
