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
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

public class PostMethodProcessor extends MethodProcessorWithWave {

  private final ProcessorContext processorContext;
  private final Deque<SortedProgramMethodSet> waves;
  private final ProgramMethodSet processed = ProgramMethodSet.create();

  private PostMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      CallGraph callGraph) {
    this.processorContext = appView.createProcessorContext();
    this.waves = createWaves(callGraph);
  }

  @Override
  public MethodProcessingContext createMethodProcessingContext(ProgramMethod method) {
    return processorContext.createMethodProcessingContext(method);
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
          LongLivedProgramMethodSetBuilder.createForIdentitySet(
              graphLensForPrimaryOptimizationPass);
    }

    public void add(ProgramMethod method) {
      methodsToReprocessBuilder.add(method);
    }

    public LongLivedProgramMethodSetBuilder<ProgramMethodSet> getMethodsToReprocessBuilder() {
      return methodsToReprocessBuilder;
    }

    public void put(ProgramMethodSet methodsToRevisit) {
      methodsToRevisit.forEach(this::add);
    }

    public void put(PostOptimization postOptimization) {
      put(postOptimization.methodsToRevisit());
    }

    // Some optimizations may change methods, creating new instances of the encoded methods with a
    // new signature. The compiler needs to update the set of methods that must be reprocessed
    // according to the graph lens.
    public void rewrittenWithLens(AppView<AppInfoWithLiveness> appView) {
      methodsToReprocessBuilder.rewrittenWithLens(appView);
    }

    PostMethodProcessor build(
        AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
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
      CallGraph callGraph =
          new PartialCallGraphBuilder(appView, methodsToReprocess).build(executorService, timing);
      return new PostMethodProcessor(appView, callGraph);
    }
  }

  private Deque<SortedProgramMethodSet> createWaves(CallGraph callGraph) {
    Deque<SortedProgramMethodSet> waves = new ArrayDeque<>();
    int waveCount = 1;
    while (!callGraph.isEmpty()) {
      SortedProgramMethodSet wave = callGraph.extractLeaves();
      waves.addLast(wave);
      if (Log.ENABLED && Log.isLoggingEnabledFor(PostMethodProcessor.class)) {
        Log.info(getClass(), "Wave #%d: %d", waveCount++, wave.size());
      }
    }
    return waves;
  }

  void forEachMethod(
      BiConsumer<ProgramMethod, MethodProcessingContext> consumer,
      OptimizationFeedbackDelayed feedback,
      ExecutorService executorService)
      throws ExecutionException {
    while (!waves.isEmpty()) {
      wave = waves.removeFirst();
      assert !wave.isEmpty();
      assert waveExtension.isEmpty();
      do {
        assert feedback.noUpdatesLeft();
        ThreadUtils.processItems(
            wave,
            method -> consumer.accept(method, createMethodProcessingContext(method)),
            executorService);
        feedback.updateVisibleOptimizationInfo();
        processed.addAll(wave);
        prepareForWaveExtensionProcessing();
      } while (!wave.isEmpty());
    }
  }
}
