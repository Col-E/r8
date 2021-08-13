// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

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
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Optimization that propagates information about arguments from call sites to method entries. */
// TODO(b/190154391): Add timing information for performance tracking.
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
  public void initializeCodeScanner() {
    codeScanner = new ArgumentPropagatorCodeScanner(appView);

    // Disable argument propagation for methods that should not be optimized.
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    // TODO(b/190154391): Consider computing the strongly connected components and running this in
    //  parallel for each scc.
    new ArgumentPropagatorUnoptimizableMethods(
            appView, immediateSubtypingInfo, codeScanner.getMethodStates())
        .disableArgumentPropagationForUnoptimizableMethods(appView.appInfo().classes());
  }

  /** Called by {@link IRConverter} prior to finalizing methods. */
  public void scan(ProgramMethod method, IRCode code, MethodProcessor methodProcessor) {
    if (codeScanner != null) {
      // TODO(b/190154391): Do we process synthetic methods using a OneTimeMethodProcessor
      //  during the primary optimization pass?
      assert methodProcessor.isPrimaryMethodProcessor();
      codeScanner.scan(method, code);
    } else {
      assert !methodProcessor.isPrimaryMethodProcessor();
    }
  }

  /**
   * Called by {@link IRConverter} *after* the primary optimization pass to populate the parameter
   * optimization info.
   */
  public void populateParameterOptimizationInfo(ExecutorService executorService)
      throws ExecutionException {
    // Unset the scanner since all code objects have been scanned at this point.
    assert appView.isAllCodeProcessed();
    MethodStateCollection codeScannerResult = codeScanner.getMethodStates();
    codeScanner = null;

    new ArgumentPropagatorOptimizationInfoPopulator(appView, codeScannerResult)
        .populateOptimizationInfo(executorService);
  }

  /**
   * Computes the strongly connected components in the program class hierarchy (where extends and
   * implements edges are treated as bidirectional).
   *
   * <p>All strongly connected components can be processed in parallel.
   */
  private static List<Set<DexProgramClass>> computeStronglyConnectedComponents(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    Set<DexProgramClass> seen = Sets.newIdentityHashSet();
    List<Set<DexProgramClass>> stronglyConnectedComponents = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (seen.contains(clazz)) {
        continue;
      }
      Set<DexProgramClass> stronglyConnectedComponent =
          computeStronglyConnectedComponent(clazz, immediateSubtypingInfo);
      stronglyConnectedComponents.add(stronglyConnectedComponent);
      seen.addAll(stronglyConnectedComponent);
    }
    return stronglyConnectedComponents;
  }

  private static Set<DexProgramClass> computeStronglyConnectedComponent(
      DexProgramClass clazz, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList(clazz);
    while (worklist.hasNext()) {
      DexProgramClass current = worklist.next();
      immediateSubtypingInfo.forEachImmediateSuperClassMatching(
          current,
          (supertype, superclass) -> superclass != null && superclass.isProgramClass(),
          (supertype, superclass) -> worklist.addIfNotSeen(superclass.asProgramClass()));
      worklist.addIfNotSeen(immediateSubtypingInfo.getSubclasses(current));
    }
    return worklist.getSeenSet();
  }

  /** Called by {@link IRConverter} to optimize method definitions. */
  public void optimizeMethodParameters() {
    // TODO(b/190154391): Remove parameters with constant values.
    // TODO(b/190154391): Remove unused parameters by simulating they are constant.
    // TODO(b/190154391): Strengthen the static type of parameters.
    // TODO(b/190154391): If we learn that a method returns a constant, then consider changing its
    //  return type to void.
  }

  /**
   * Called by {@link IRConverter} to add all methods that require reprocessing to {@param
   * postMethodProcessorBuilder}.
   */
  public void enqueueMethodsForProcessing(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            CallSiteOptimizationInfo callSiteOptimizationInfo =
                method.getDefinition().getCallSiteOptimizationInfo();
            if (callSiteOptimizationInfo.isConcreteCallSiteOptimizationInfo()) {
              postMethodProcessorBuilder.add(method);
            }
          });
    }
  }
}
