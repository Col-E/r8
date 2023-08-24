// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.VirtualRootMethodsAnalysis;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.ArgumentPropagatorReprocessingCriteriaCollection;
import com.android.tools.r8.optimize.argumentpropagation.unusedarguments.EffectivelyUnusedArgumentsAnalysis;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

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

  /** Collects and solves constraints for effectively unused argument removal. */
  private EffectivelyUnusedArgumentsAnalysis effectivelyUnusedArgumentsAnalysis;

  /**
   * Analyzes the uses of arguments in methods to determine when reprocessing of methods will likely
   * not lead to any additional code optimizations.
   */
  private ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection;

  public ArgumentPropagator(AppView<AppInfoWithLiveness> appView) {
    assert appView.enableWholeProgramOptimizations();
    assert appView.options().isOptimizing();
    assert appView.options().callSiteOptimizationOptions().isEnabled();
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

    reprocessingCriteriaCollection = new ArgumentPropagatorReprocessingCriteriaCollection(appView);
    codeScanner = new ArgumentPropagatorCodeScanner(appView, reprocessingCriteriaCollection);
    effectivelyUnusedArgumentsAnalysis = new EffectivelyUnusedArgumentsAnalysis(appView);

    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> stronglyConnectedProgramClasses =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    ThreadUtils.processItems(
        stronglyConnectedProgramClasses,
        classes -> {
          // Disable argument propagation for methods that should not be optimized by setting their
          // method state to unknown.
          new ArgumentPropagatorUnoptimizableMethods(
                  appView, immediateSubtypingInfo, codeScanner.getMethodStates())
              .initializeUnoptimizableMethodStates(classes);

          // Compute the mapping from virtual methods to their root virtual method and the set of
          // monomorphic virtual methods.
          new VirtualRootMethodsAnalysis(appView, immediateSubtypingInfo)
              .initializeVirtualRootMethods(classes, codeScanner);

          // Find the virtual methods in the strongly connected component that only have monomorphic
          // calls.
          effectivelyUnusedArgumentsAnalysis.initializeOptimizableVirtualMethods(classes);
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

      assert effectivelyUnusedArgumentsAnalysis != null;
      effectivelyUnusedArgumentsAnalysis.scan(method, code);

      assert reprocessingCriteriaCollection != null;
      reprocessingCriteriaCollection.analyzeArgumentUses(method, code);
    } else {
      assert !methodProcessor.isPrimaryMethodProcessor();
      assert effectivelyUnusedArgumentsAnalysis == null;
      assert !methodProcessor.isPostMethodProcessor() || reprocessingCriteriaCollection == null;
    }
  }

  public void publishDelayedReprocessingCriteria() {
    assert reprocessingCriteriaCollection != null;
    reprocessingCriteriaCollection.publishDelayedReprocessingCriteria();
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
      PrimaryR8IRConverter converter,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    assert reprocessingCriteriaCollection.verifyNoDelayedReprocessingCriteria();

    timing.begin("Argument propagator");

    // Compute the strongly connected program components for parallel execution.
    timing.begin("Compute components");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> stronglyConnectedProgramComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    timing.end();

    // Set the optimization info on each method.
    Map<Set<DexProgramClass>, DexMethodSignatureSet> interfaceDispatchOutsideProgram =
        new IdentityHashMap<>();
    populateParameterOptimizationInfo(
        converter,
        immediateSubtypingInfo,
        stronglyConnectedProgramComponents,
        (stronglyConnectedProgramComponent, signature) -> {
          interfaceDispatchOutsideProgram
              .computeIfAbsent(
                  stronglyConnectedProgramComponent, (unused) -> DexMethodSignatureSet.create())
              .add(signature);
        },
        postMethodProcessorBuilder,
        executorService,
        timing);

    // Using the computed optimization info, build a graph lens that describes the mapping from
    // methods with constant parameters to methods with the constant parameters removed.
    Set<DexProgramClass> affectedClasses = SetUtils.newConcurrentHashSet();
    ArgumentPropagatorGraphLens graphLens =
        new ArgumentPropagatorProgramOptimizer(
                appView, immediateSubtypingInfo, interfaceDispatchOutsideProgram)
            .run(stronglyConnectedProgramComponents, affectedClasses::add, executorService, timing);

    // Find all the code objects that need reprocessing.
    new ArgumentPropagatorMethodReprocessingEnqueuer(appView, reprocessingCriteriaCollection)
        .enqueueMethodForReprocessing(
            graphLens, postMethodProcessorBuilder, executorService, timing);
    reprocessingCriteriaCollection = null;

    // Finally, apply the graph lens to the program (i.e., remove constant parameters from method
    // definitions).
    new ArgumentPropagatorApplicationFixer(appView, graphLens)
        .fixupApplication(affectedClasses, executorService, timing);

    timing.end();

    // Ensure determinism of method-to-reprocess set.
    appView.testing().checkDeterminism(postMethodProcessorBuilder::dump);

    appView.notifyOptimizationFinishedForTesting();
  }

  /**
   * Called by {@link IRConverter} *after* the primary optimization pass to populate the parameter
   * optimization info.
   */
  private void populateParameterOptimizationInfo(
      PrimaryR8IRConverter converter,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      BiConsumer<Set<DexProgramClass>, DexMethodSignature> interfaceDispatchOutsideProgram,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    // Unset the scanner since all code objects have been scanned at this point.
    assert appView.isAllCodeProcessed();
    MethodStateCollectionByReference codeScannerResult = codeScanner.getMethodStates();
    appView.testing().argumentPropagatorEventConsumer.acceptCodeScannerResult(codeScannerResult);
    codeScanner = null;

    postMethodProcessorBuilder.rewrittenWithLens(appView);

    timing.begin("Compute optimization info");
    new ArgumentPropagatorOptimizationInfoPopulator(
            appView,
            converter,
            immediateSubtypingInfo,
            codeScannerResult,
            postMethodProcessorBuilder,
            stronglyConnectedProgramComponents,
            interfaceDispatchOutsideProgram)
        .populateOptimizationInfo(executorService, timing);
    timing.end();

    timing.begin("Compute unused arguments");
    effectivelyUnusedArgumentsAnalysis.computeEffectivelyUnusedArguments();
    effectivelyUnusedArgumentsAnalysis = null;
    timing.end();
  }

  /**
   * Called by {@link IRConverter} at the end of a wave if a method is pruned by an optimization.
   *
   * <p>We only prune (1) direct single caller methods and (2) isX()/asX() virtual method overrides.
   * For (2), we always transfer the argument information for the isX()/asX() method to its parent
   * method using {@link #transferArgumentInformation(ProgramMethod, ProgramMethod)}, which unsets
   * the argument information for the override.
   *
   * <p>Therefore, we assert that we only find a method state for direct methods.
   */
  public void onMethodPruned(ProgramMethod method) {
    if (codeScanner != null) {
      MethodState methodState = codeScanner.getMethodStates().removeOrElse(method, null);
      assert methodState == null || method.getDefinition().belongsToDirectPool();
    }

    assert effectivelyUnusedArgumentsAnalysis != null;
    effectivelyUnusedArgumentsAnalysis.onMethodPruned(method);
  }

  public void onMethodCodePruned(ProgramMethod method) {
    assert effectivelyUnusedArgumentsAnalysis != null;
    effectivelyUnusedArgumentsAnalysis.onMethodCodePruned(method);
  }
}
