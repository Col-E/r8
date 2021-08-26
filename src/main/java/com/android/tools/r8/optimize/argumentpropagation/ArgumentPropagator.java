// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.optimize.argumentpropagation.utils.StronglyConnectedProgramClasses.computeStronglyConnectedProgramClasses;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.VirtualRootMethodsAnalysis;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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
              .extendVirtualRootMethods(appView.appInfo().classes(), codeScanner);
        },
        executorService);

    timing.end();
    timing.end();
  }

  /** Called by {@link IRConverter} prior to finalizing methods. */
  public void scan(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing) {
    if (codeScanner != null) {
      // TODO(b/190154391): Do we process synthetic methods using a OneTimeMethodProcessor
      //  during the primary optimization pass?
      assert methodProcessor.isPrimaryMethodProcessor();
      codeScanner.scan(method, code, timing);
    } else {
      assert !methodProcessor.isPrimaryMethodProcessor();
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
    populateParameterOptimizationInfo(executorService, timing);
    optimizeMethodParameters();
    enqueueMethodsForProcessing(postMethodProcessorBuilder);
    timing.end();
  }

  /**
   * Called by {@link IRConverter} *after* the primary optimization pass to populate the parameter
   * optimization info.
   */
  private void populateParameterOptimizationInfo(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Unset the scanner since all code objects have been scanned at this point.
    assert appView.isAllCodeProcessed();
    MethodStateCollectionByReference codeScannerResult = codeScanner.getMethodStates();
    appView.testing().argumentPropagatorEventConsumer.acceptCodeScannerResult(codeScannerResult);
    codeScanner = null;

    timing.begin("Compute optimization info");
    new ArgumentPropagatorOptimizationInfoPopulator(appView, codeScannerResult)
        .populateOptimizationInfo(executorService, timing);
    timing.end();
  }

  /** Called by {@link IRConverter} to optimize method definitions. */
  private void optimizeMethodParameters() {
    // TODO(b/190154391): Remove parameters with constant values.
    // TODO(b/190154391): Remove unused parameters by simulating they are constant.
    // TODO(b/190154391): Strengthen the static type of parameters.
    // TODO(b/190154391): If we learn that a method returns a constant, then consider changing its
    //  return type to void.
    // TODO(b/69963623): If we optimize a method to be unconditionally throwing (because it has a
    //  bottom parameter), then for each caller that becomes unconditionally throwing, we could
    //  also enqueue the caller's callers for reprocessing. This would propagate the throwing
    //  information to all call sites.
  }

  /**
   * Called by {@link IRConverter} to add all methods that require reprocessing to {@param
   * postMethodProcessorBuilder}.
   */
  private void enqueueMethodsForProcessing(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            CallSiteOptimizationInfo callSiteOptimizationInfo =
                method.getDefinition().getCallSiteOptimizationInfo();
            if (callSiteOptimizationInfo.isConcreteCallSiteOptimizationInfo()
                && !appView.appInfo().isNeverReprocessMethod(method.getReference())) {
              postMethodProcessorBuilder.add(method);
            }
          });
    }
  }
}
