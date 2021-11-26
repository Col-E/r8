// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Finds the methods in the program that should be reprocessed due to argument propagation. */
public class ArgumentPropagatorMethodReprocessingEnqueuer {

  private final AppView<AppInfoWithLiveness> appView;

  public ArgumentPropagatorMethodReprocessingEnqueuer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  /**
   * Called indirectly from {@link IRConverter} to add all methods that require reprocessing to
   * {@param postMethodProcessorBuilder}.
   */
  public void enqueueMethodForReprocessing(
      ArgumentPropagatorGraphLens graphLens,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Enqueue methods for reprocessing");

    // Bring the methods to reprocess set up-to-date with the current graph lens (i.e., the one
    // prior to the argument propagator lens, which has not yet been installed!).
    timing.begin("Rewrite methods to reprocess");
    LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodsToReprocessBuilder =
        postMethodProcessorBuilder
            .getMethodsToReprocessBuilder()
            .rewrittenWithLens(appView.graphLens());
    timing.end();

    timing.begin("Enqueue methods with non-trivial info");
    enqueueMethodsWithNonTrivialOptimizationInfo(methodsToReprocessBuilder);
    timing.end();

    timing.begin("Enqueue affected methods");
    if (graphLens != null) {
      enqueueAffectedCallers(graphLens, methodsToReprocessBuilder, executorService);
    }
    timing.end();

    timing.end();
  }

  private void enqueueMethodsWithNonTrivialOptimizationInfo(
      LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodsToReprocessBuilder) {
    GraphLens currentGraphLens = appView.graphLens();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            CallSiteOptimizationInfo callSiteOptimizationInfo =
                method.getOptimizationInfo().getArgumentInfos();
            if (callSiteOptimizationInfo.isConcreteCallSiteOptimizationInfo()
                && !appView.appInfo().isNeverReprocessMethod(method)) {
              methodsToReprocessBuilder.add(method, currentGraphLens);
              appView.testing().callSiteOptimizationInfoInspector.accept(method);
            }
          });
    }
  }

  // TODO(b/190154391): This could invalidate the @NeverReprocessMethod testing annotations (non
  //  critical). If @NeverReprocessMethod is used, we would need to scan the application to mark
  //  methods as unoptimizable prior to removing parameters from the application.
  private void enqueueAffectedCallers(
      ArgumentPropagatorGraphLens graphLens,
      LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodsToReprocessBuilder,
      ExecutorService executorService)
      throws ExecutionException {
    Collection<List<ProgramMethod>> methodsToReprocess =
        ThreadUtils.processItemsWithResults(
            appView.appInfo().classes(),
            clazz -> {
              List<ProgramMethod> methodsToReprocessInClass = new ArrayList<>();
              clazz.forEachProgramMethodMatching(
                  DexEncodedMethod::hasCode,
                  method -> {
                    if (graphLens.internalGetNextMethodSignature(method.getReference())
                        != method.getReference()) {
                      if (!method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite()) {
                        methodsToReprocessInClass.add(method);
                      }
                    } else {
                      AffectedMethodUseRegistry registry =
                          new AffectedMethodUseRegistry(appView, method, graphLens);
                      if (method.registerCodeReferencesWithResult(registry)) {
                        assert !method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite();
                        methodsToReprocessInClass.add(method);
                      }
                    }
                  });
              return methodsToReprocessInClass;
            },
            executorService);
    GraphLens currentGraphLens = appView.graphLens();
    methodsToReprocess.forEach(
        methodsToReprocessForClass ->
            methodsToReprocessBuilder.addAll(methodsToReprocessForClass, currentGraphLens));
  }

  static class AffectedMethodUseRegistry extends UseRegistryWithResult<Boolean, ProgramMethod> {

    private final AppView<AppInfoWithLiveness> appView;
    private final ArgumentPropagatorGraphLens graphLens;

    AffectedMethodUseRegistry(
        AppView<AppInfoWithLiveness> appView,
        ProgramMethod context,
        ArgumentPropagatorGraphLens graphLens) {
      super(appView, context, false);
      this.appView = appView;
      this.graphLens = graphLens;
    }

    private void markAffected() {
      setResult(Boolean.TRUE);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvokeMethod(method);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvokeMethod(method);
    }

    private void registerInvokeMethod(DexMethod method) {
      SingleResolutionResult resolutionResult =
          appView.appInfo().unsafeResolveMethodDueToDexFormat(method).asSingleResolution();
      if (resolutionResult == null || !resolutionResult.getResolvedHolder().isProgramClass()) {
        return;
      }

      ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
      DexMethod rewrittenMethodReference =
          graphLens.internalGetNextMethodSignature(resolvedMethod.getReference());
      if (rewrittenMethodReference != resolvedMethod.getReference()) {
        markAffected();
      }
    }

    @Override
    public void registerInitClass(DexType type) {}

    @Override
    public void registerInstanceFieldRead(DexField field) {}

    @Override
    public void registerInstanceFieldWrite(DexField field) {}

    @Override
    public void registerStaticFieldRead(DexField field) {}

    @Override
    public void registerStaticFieldWrite(DexField field) {}

    @Override
    public void registerTypeReference(DexType type) {}
  }
}
