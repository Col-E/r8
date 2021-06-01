// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PostMethodProcessor extends MethodProcessorWithWave {

  private final ProcessorContext processorContext;
  private final AppView<AppInfoWithLiveness> appView;
  private final Collection<CodeOptimization> defaultCodeOptimizations;
  private final Map<DexMethod, Collection<CodeOptimization>> methodsMap;
  private final Deque<SortedProgramMethodSet> waves;
  private final ProgramMethodSet processed = ProgramMethodSet.create();

  private PostMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      Collection<CodeOptimization> defaultCodeOptimizations,
      Map<DexMethod, Collection<CodeOptimization>> methodsMap,
      CallGraph callGraph) {
    this.processorContext = appView.createProcessorContext();
    this.appView = appView;
    this.defaultCodeOptimizations = defaultCodeOptimizations;
    this.methodsMap = methodsMap;
    this.waves = createWaves(callGraph);
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    assert !wave.contains(method);
    return !processed.contains(method);
  }

  public static class Builder {

    private final Collection<CodeOptimization> defaultCodeOptimizations;
    private final LongLivedProgramMethodSetBuilder<?> methodsToReprocess =
        LongLivedProgramMethodSetBuilder.createForIdentitySet();
    private final Map<DexMethod, Collection<CodeOptimization>> optimizationsMap =
        new IdentityHashMap<>();

    Builder(Collection<CodeOptimization> defaultCodeOptimizations) {
      this.defaultCodeOptimizations = defaultCodeOptimizations;
    }

    private void put(
        ProgramMethodSet methodsToRevisit, Collection<CodeOptimization> codeOptimizations) {
      if (codeOptimizations.isEmpty()) {
        // Nothing to conduct.
        return;
      }
      for (ProgramMethod method : methodsToRevisit) {
        methodsToReprocess.add(method);
        optimizationsMap
            .computeIfAbsent(
                method.getReference(),
                // Optimization order might matter, hence a collection that preserves orderings.
                k -> new LinkedHashSet<>())
            .addAll(codeOptimizations);
      }
    }

    public void put(ProgramMethodSet methodsToRevisit) {
      put(methodsToRevisit, defaultCodeOptimizations);
    }

    public void put(PostOptimization postOptimization) {
      Collection<CodeOptimization> codeOptimizations =
          postOptimization.codeOptimizationsForPostProcessing();
      if (codeOptimizations == null) {
        codeOptimizations = defaultCodeOptimizations;
      }
      put(postOptimization.methodsToRevisit(), codeOptimizations);
    }

    // Some optimizations may change methods, creating new instances of the encoded methods with a
    // new signature. The compiler needs to update the set of methods that must be reprocessed
    // according to the graph lens.
    public void rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLens applied) {
      methodsToReprocess.rewrittenWithLens(appView, applied);
      Map<DexMethod, Collection<CodeOptimization>> newOptimizationsMap = new IdentityHashMap<>();
      optimizationsMap.forEach(
          (method, optimizations) ->
              newOptimizationsMap.put(
                  appView.graphLens().getRenamedMethodSignature(method, applied), optimizations));
      optimizationsMap.clear();
      optimizationsMap.putAll(newOptimizationsMap);
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
      if (methodsToReprocess.isEmpty()) {
        // Nothing to revisit.
        return null;
      }
      CallGraph callGraph =
          new PartialCallGraphBuilder(
                  appView, methodsToReprocess.build(appView, appView.graphLens()))
              .build(executorService, timing);
      return new PostMethodProcessor(
          appView, defaultCodeOptimizations, optimizationsMap, callGraph);
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

  @Override
  protected void prepareForWaveExtensionProcessing() {
    waveExtension.forEach(
        method -> {
          assert !methodsMap.containsKey(method.getReference());
          methodsMap.put(method.getReference(), defaultCodeOptimizations);
        });
    super.prepareForWaveExtensionProcessing();
  }

  void forEachWaveWithExtension(
      OptimizationFeedbackDelayed feedback, ExecutorService executorService)
      throws ExecutionException {
    while (!waves.isEmpty()) {
      wave = waves.removeFirst();
      assert !wave.isEmpty();
      assert waveExtension.isEmpty();
      do {
        assert feedback.noUpdatesLeft();
        ThreadUtils.processItems(
            wave,
            method -> {
              Collection<CodeOptimization> codeOptimizations =
                  methodsMap.get(method.getReference());
              assert codeOptimizations != null && !codeOptimizations.isEmpty();
              forEachMethod(method, codeOptimizations, feedback);
            },
            executorService);
        feedback.updateVisibleOptimizationInfo();
        processed.addAll(wave);
        prepareForWaveExtensionProcessing();
      } while (!wave.isEmpty());
    }
  }

  private void forEachMethod(
      ProgramMethod method,
      Collection<CodeOptimization> codeOptimizations,
      OptimizationFeedback feedback) {
    // TODO(b/140766440): Make IRConverter#process receive a list of CodeOptimization to conduct.
    //   Then, we can share IRCode creation there.
    if (appView.options().skipIR) {
      feedback.markProcessed(method.getDefinition(), ConstraintWithTarget.NEVER);
      return;
    }
    IRCode code = method.buildIR(appView);
    if (code == null) {
      feedback.markProcessed(method.getDefinition(), ConstraintWithTarget.NEVER);
      return;
    }
    // TODO(b/140768815): Reprocessing may trigger more methods to revisit. Update waves on-the-fly.
    for (CodeOptimization codeOptimization : codeOptimizations) {
      codeOptimization.optimize(
          code, feedback, this, processorContext.createMethodProcessingContext(method));
    }
  }
}
