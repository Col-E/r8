// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Finds the methods in the program that should be reprocessed due to non-trivial method resolution
 * optimization info.
 */
class MethodResolutionOptimizationInfoReprocessingEnqueuer {

  private final AppView<AppInfoWithLiveness> appView;

  MethodResolutionOptimizationInfoReprocessingEnqueuer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void enqueueMethodForReprocessing(
      ExecutorService executorService, PostMethodProcessor.Builder postMethodProcessorBuilder)
      throws ExecutionException {
    // Bring the methods to reprocess set up-to-date with the current graph lens.
    postMethodProcessorBuilder.rewrittenWithLens(appView);
    enqueueAffectedCallers(postMethodProcessorBuilder, executorService);
  }

  private void enqueueAffectedCallers(
      PostMethodProcessor.Builder postMethodProcessorBuilder, ExecutorService executorService)
      throws ExecutionException {
    GraphLens currentGraphLens = appView.graphLens();
    Collection<List<ProgramMethod>> methodsToReprocess =
        ThreadUtils.processItemsWithResults(
            appView.appInfo().classes(),
            clazz -> {
              List<ProgramMethod> methodsToReprocessInClass = new ArrayList<>();
              clazz.forEachProgramMethodMatching(
                  DexEncodedMethod::hasCode,
                  method -> {
                    if (!postMethodProcessorBuilder.contains(method, currentGraphLens)
                        && !appView.appInfo().isNeverReprocessMethod(method)) {
                      AffectedMethodUseRegistry registry =
                          new AffectedMethodUseRegistry(appView, method);
                      if (method.registerCodeReferencesWithResult(registry)) {
                        methodsToReprocessInClass.add(method);
                      }
                    }
                  });
              return methodsToReprocessInClass;
            },
            appView.options().getThreadingModule(),
            executorService);
    methodsToReprocess.forEach(
        methodsToReprocessInClass ->
            postMethodProcessorBuilder.addAll(methodsToReprocessInClass, currentGraphLens));
  }

  static class AffectedMethodUseRegistry extends UseRegistryWithResult<Boolean, ProgramMethod> {

    private final AppView<AppInfoWithLiveness> appViewWithLiveness;

    AffectedMethodUseRegistry(
        AppView<AppInfoWithLiveness> appViewWithLiveness, ProgramMethod context) {
      super(appViewWithLiveness, context, false);
      this.appViewWithLiveness = appViewWithLiveness;
    }

    private void markAffected() {
      setResult(Boolean.TRUE);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      DexMethod rewrittenMethod =
          appViewWithLiveness.graphLens().lookupInvokeDirect(method, getContext()).getReference();
      registerInvokeMethod(rewrittenMethod);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      DexMethod rewrittenMethod =
          appViewWithLiveness
              .graphLens()
              .lookupInvokeInterface(method, getContext())
              .getReference();
      registerInvokeMethod(rewrittenMethod);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      DexMethod rewrittenMethod =
          appViewWithLiveness.graphLens().lookupInvokeStatic(method, getContext()).getReference();
      registerInvokeMethod(rewrittenMethod);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      DexMethod rewrittenMethod =
          appViewWithLiveness.graphLens().lookupInvokeSuper(method, getContext()).getReference();
      registerInvokeMethod(rewrittenMethod);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      DexMethod rewrittenMethod =
          appViewWithLiveness.graphLens().lookupInvokeVirtual(method, getContext()).getReference();
      registerInvokeMethod(rewrittenMethod);
    }

    private void registerInvokeMethod(DexMethod method) {
      SingleResolutionResult<?> resolutionResult =
          appViewWithLiveness
              .appInfo()
              .unsafeResolveMethodDueToDexFormatLegacy(method)
              .asSingleResolution();
      if (resolutionResult == null) {
        return;
      }

      MethodResolutionOptimizationInfoCollection methodResolutionOptimizationInfoCollection =
          appViewWithLiveness.getMethodResolutionOptimizationInfoCollection();
      MethodOptimizationInfo optimizationInfo =
          methodResolutionOptimizationInfoCollection.get(
              resolutionResult.getResolvedMethod(), resolutionResult.getResolvedHolder());
      if (!optimizationInfo.isDefault()) {
        markAffected();
      }
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {}

    @Override
    public void registerInstanceFieldWrite(DexField field) {}

    @Override
    public void registerStaticFieldRead(DexField field) {}

    @Override
    public void registerStaticFieldWrite(DexField field) {}

    @Override
    public void registerInitClass(DexType type) {}

    @Override
    public void registerTypeReference(DexType type) {}
  }
}
