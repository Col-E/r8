// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.optimize.argumentpropagation.utils.StronglyConnectedProgramClasses.computeStronglyConnectedProgramClasses;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.VirtualRootMethodsAnalysis;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.ArgumentPropagatorReprocessingCriteriaCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Optimization that propagates information about arguments from call sites to method entries. */
public class ArgumentPropagator {

  private final AppView<AppInfoWithLiveness> appView;

  /**
   * Collects information about arguments from call sites, meanwhile pruning redundant information.
   *
   * <p>The data held by this instance is incomplete and should not be used for optimization until
   * processed by {@link ArgumentPropagatorOptimizationInfoPopulator}.
   */
  private ArgumentPropagatorCodeScanner codeScanner;

  /**
   * Analyzes the uses of arguments in methods to determine when reprocessing of methods will likely
   * not lead to any additional code optimizations.
   */
  private ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection;

  public ArgumentPropagator(AppView<AppInfoWithLiveness> appView) {
    assert appView.enableWholeProgramOptimizations();
    assert appView.options().isOptimizing();
    assert appView.options().callSiteOptimizationOptions().isEnabled();
    assert appView
        .options()
        .callSiteOptimizationOptions()
        .isExperimentalArgumentPropagationEnabled();
    this.appView = appView;
  }

  /**
   * Called by {@link IRConverter} *before* the primary optimization pass to setup the scanner for
   * collecting argument information from the code objects.
   */
  public void initializeCodeScanner(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();

    timing.begin("Argument propagator");
    timing.begin("Initialize code scanner");

    codeScanner = new ArgumentPropagatorCodeScanner(appView);
    reprocessingCriteriaCollection = new ArgumentPropagatorReprocessingCriteriaCollection(appView);

    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> stronglyConnectedProgramClasses =
        computeStronglyConnectedProgramClasses(appView, immediateSubtypingInfo);
    ThreadUtils.processItems(
        stronglyConnectedProgramClasses,
        classes -> {
          // Disable argument propagation for methods that should not be optimized by setting their
          // method state to unknown.
          new ArgumentPropagatorUnoptimizableMethods(
                  appView, immediateSubtypingInfo, codeScanner.getMethodStates())
              .disableArgumentPropagationForUnoptimizableMethods(classes);

          // Compute the mapping from virtual methods to their root virtual method and the set of
          // monomorphic virtual methods.
          new VirtualRootMethodsAnalysis(appView, immediateSubtypingInfo)
              .extendVirtualRootMethods(classes, codeScanner);
        },
        executorService);

    timing.end();
    timing.end();
  }

  /** Called by {@link IRConverter} prior to finalizing methods. */
  public void scan(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing) {
    if (codeScanner != null) {
      assert methodProcessor.isPrimaryMethodProcessor();
      codeScanner.scan(method, code, timing);

      assert reprocessingCriteriaCollection != null;
      reprocessingCriteriaCollection.analyzeArgumentUses(method, code);
    } else {
      assert !methodProcessor.isPrimaryMethodProcessor();
      assert reprocessingCriteriaCollection == null;
    }
  }

  public void transferArgumentInformation(ProgramMethod from, ProgramMethod to) {
    assert codeScanner != null;
    MethodStateCollectionByReference methodStates = codeScanner.getMethodStates();
    MethodState methodState = methodStates.remove(from);
    if (!methodState.isBottom()) {
      methodStates.addMethodState(appView, to, methodState);
    }
  }

  public void tearDownCodeScanner(
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    timing.begin("Argument propagator");

    // Compute the strongly connected program components for parallel execution.
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> stronglyConnectedProgramComponents =
        computeStronglyConnectedProgramClasses(appView, immediateSubtypingInfo);

    // Set the optimization info on each method.
    populateParameterOptimizationInfo(
        immediateSubtypingInfo, stronglyConnectedProgramComponents, executorService, timing);

    // Using the computed optimization info, build a graph lens that describes the mapping from
    // methods with constant parameters to methods with the constant parameters removed.
    Set<DexProgramClass> affectedClasses = Sets.newConcurrentHashSet();
    ArgumentPropagatorGraphLens graphLens =
        optimizeMethodParameters(
            stronglyConnectedProgramComponents, affectedClasses::add, executorService);

    // Find all the code objects that need reprocessing.
    new ArgumentPropagatorMethodReprocessingEnqueuer(appView)
        .enqueueMethodForReprocessing(graphLens, postMethodProcessorBuilder, executorService);

    // Finally, apply the graph lens to the program (i.e., remove constant parameters from method
    // definitions).
    new ArgumentPropagatorApplicationFixer(appView, graphLens)
        .fixupApplication(affectedClasses, executorService);

    timing.end();
  }

  /**
   * Called by {@link IRConverter} *after* the primary optimization pass to populate the parameter
   * optimization info.
   */
  private void populateParameterOptimizationInfo(
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    // Unset the scanner since all code objects have been scanned at this point.
    assert appView.isAllCodeProcessed();
    MethodStateCollectionByReference codeScannerResult = codeScanner.getMethodStates();
    appView.testing().argumentPropagatorEventConsumer.acceptCodeScannerResult(codeScannerResult);
    codeScanner = null;

    timing.begin("Compute optimization info");
    new ArgumentPropagatorOptimizationInfoPopulator(
            appView,
            immediateSubtypingInfo,
            codeScannerResult,
            reprocessingCriteriaCollection,
            stronglyConnectedProgramComponents)
        .populateOptimizationInfo(executorService, timing);
    reprocessingCriteriaCollection = null;
    timing.end();
  }

  /** Called by {@link IRConverter} to optimize method definitions. */
  private ArgumentPropagatorGraphLens optimizeMethodParameters(
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      Consumer<DexProgramClass> affectedClassConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    Collection<ArgumentPropagatorGraphLens.Builder> partialGraphLensBuilders =
        ThreadUtils.processItemsWithResults(
            stronglyConnectedProgramComponents,
            classes ->
                new ArgumentPropagatorProgramOptimizer(appView)
                    .optimize(classes, affectedClassConsumer),
            executorService);

    // Merge all the partial, disjoint graph lens builders into a single graph lens.
    ArgumentPropagatorGraphLens.Builder graphLensBuilder =
        ArgumentPropagatorGraphLens.builder(appView);
    partialGraphLensBuilders.forEach(graphLensBuilder::mergeDisjoint);
    return graphLensBuilder.build();
  }
}
