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
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.ArgumentPropagatorReprocessingCriteriaCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Finds the methods in the program that should be reprocessed due to argument propagation. */
public class ArgumentPropagatorMethodReprocessingEnqueuer {

  private final AppView<AppInfoWithLiveness> appView;
  private final ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection;

  public ArgumentPropagatorMethodReprocessingEnqueuer(
      AppView<AppInfoWithLiveness> appView,
      ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection) {
    this.appView = appView;
    this.reprocessingCriteriaCollection = reprocessingCriteriaCollection;
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
    postMethodProcessorBuilder.rewrittenWithLens(appView);
    timing.end();

    timing.begin("Enqueue methods with non-trivial info");
    enqueueAffectedCallees(graphLens, postMethodProcessorBuilder);
    timing.end();

    timing.begin("Enqueue affected methods");
    if (graphLens != null) {
      enqueueAffectedCallers(graphLens, postMethodProcessorBuilder, executorService);
    }
    timing.end();

    timing.end();
  }

  private void enqueueAffectedCallees(
      ArgumentPropagatorGraphLens graphLens,
      PostMethodProcessor.Builder postMethodProcessorBuilder) {
    GraphLens currentGraphLens = appView.graphLens();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            if (method.getDefinition().getCode().isSharedCodeObject()) {
              return;
            }

            if (graphLens != null) {
              DexMethod rewrittenMethodSignature =
                  graphLens.getNextMethodSignature(method.getReference());
              if (graphLens.hasPrototypeChanges(rewrittenMethodSignature)) {
                assert !appView.appInfo().isNeverReprocessMethod(method);
                postMethodProcessorBuilder.add(method, currentGraphLens);
                appView.testing().callSiteOptimizationInfoInspector.accept(method);
                return;
              }
            }

            CallSiteOptimizationInfo callSiteOptimizationInfo =
                method.getOptimizationInfo().getArgumentInfos();
            if (reprocessingCriteriaCollection
                    .getReprocessingCriteria(method)
                    .shouldReprocess(appView, method, callSiteOptimizationInfo)
                && !appView.appInfo().isNeverReprocessMethod(method)) {
              postMethodProcessorBuilder.add(method, currentGraphLens);
              appView.testing().callSiteOptimizationInfoInspector.accept(method);
            }
          });
    }
  }

  @SuppressWarnings("HidingField")
  // TODO(b/190154391): This could invalidate the @NeverReprocessMethod testing annotations (non
  //  critical). If @NeverReprocessMethod is used, we would need to scan the application to mark
  //  methods as unoptimizable prior to removing parameters from the application.
  private void enqueueAffectedCallers(
      ArgumentPropagatorGraphLens graphLens,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService)
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
                    if (!postMethodProcessorBuilder.contains(method, currentGraphLens)) {
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
            appView.options().getThreadingModule(),
            executorService);
    methodsToReprocess.forEach(
        methodsToReprocessInClass ->
            postMethodProcessorBuilder.addAll(methodsToReprocessInClass, currentGraphLens));
  }

  static class AffectedMethodUseRegistry extends UseRegistryWithResult<Boolean, ProgramMethod> {

    private final AppView<AppInfoWithLiveness> appViewWithLiveness;
    private final ArgumentPropagatorGraphLens graphLens;

    AffectedMethodUseRegistry(
        AppView<AppInfoWithLiveness> appViewWithLiveness,
        ProgramMethod context,
        ArgumentPropagatorGraphLens graphLens) {
      super(appViewWithLiveness, context, false);
      this.appViewWithLiveness = appViewWithLiveness;
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

    @SuppressWarnings("ReferenceEquality")
    private void registerInvokeMethod(DexMethod method) {
      SingleResolutionResult<?> resolutionResult =
          appViewWithLiveness
              .appInfo()
              .unsafeResolveMethodDueToDexFormatLegacy(method)
              .asSingleResolution();
      if (resolutionResult == null || !resolutionResult.getResolvedHolder().isProgramClass()) {
        return;
      }

      ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
      DexMethod rewrittenMethodReference =
          graphLens.getNextMethodSignature(resolvedMethod.getReference());
      if (rewrittenMethodReference != resolvedMethod.getReference()
          || graphLens.hasPrototypeChanges(rewrittenMethodReference)) {
        markAffected();
      }
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    @SuppressWarnings("ReferenceEquality")
    private void registerFieldAccess(DexField field) {
      FieldResolutionResult resolutionResult = appViewWithLiveness.appInfo().resolveField(field);
      if (resolutionResult.getSingleProgramField() == null) {
        return;
      }

      ProgramField resolvedField = resolutionResult.getSingleProgramField();
      DexField rewrittenFieldReference =
          graphLens.getNextFieldSignature(resolvedField.getReference());
      if (rewrittenFieldReference != resolvedField.getReference()) {
        markAffected();
      }
    }

    @Override
    public void registerInitClass(DexType type) {}

    @Override
    public void registerTypeReference(DexType type) {}
  }
}
